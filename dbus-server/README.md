# Databus Server端运行环境准备

## 1. Oracle环境

### 1.1 数据库用户授权

#### 1.1.1 以oracle sys管理员登陆
```
sqlplus /nolog
conn sys/{password} as sysdba
```


#### 1.1.2 授权
```
grant create session, create table, create view, create sequence, create procedure, create trigger, create type, create job  to ${USER};
grant query rewrite to ${USER};
grant execute on dbms_alert to ${USER};
grant execute on sys.dbms_lock to ${USER};
grant select on sys.v_$database to ${USER};
grant execute on sys.dbms_aq to ${USER};
grant execute on sys.dbms_aqadm to ${USER};
grant execute on sys.dbms_aqin to ${USER};
grant execute on sys.dbms_aq_bqview to ${USER};
```

### 1.2 创建server端必须 sequences、tables、procedures、packages、views、定时任务

#### 1.2.1 创建scn序列
```
create sequence SY$SCN_SEQ
 increment by 1
 start with 1000
 maxvalue 999999999999999999999999999
 minvalue 1000
 nocycle
 cache 20
```

#### 1.2.2 创建id序列
```
create sequence SY$ID_SEQ
increment by 1
start with 1000
    maxvalue 999999999999999999999999999
    minvalue 1000
    nocycle
    nocache
/
```

#### 1.2.2 创建tables

##### 1.2.2.1 创建 sy$sources table
```
---PROMPT creating sources table
create table sy$sources (
name    varchar2(30),
  bitnum  number constraint sy$sources_n1 not null
)
INITRANS 1
MAXTRANS 255
PCTUSED 80
PCTFREE 10 TABLESPACE ${USER}
/

--- name 添加唯一性约束
ALTER TABLE sy$sources
  ADD CONSTRAINT sy$sources_name_unique
UNIQUE (name)
/
```

##### 1.2.2.2 创建 sy$txlog table
```
---PROMPT creating table sy\$txlog
create table sy$txlog (
  txn    number,
scn    number constraint sy$txlog_n1 not null,
mask   number,
  ts     timestamp default systimestamp constraint sy$txlog_n2 not null
) rowdependencies
INITRANS 1
MAXTRANS 255
PCTUSED 80
PCTFREE 10 TABLESPACE ${USER}
/

--- Adding primary key constraints
ALTER TABLE SY$TXLOG
ADD (CONSTRAINT SY$TXLOG_PK PRIMARY KEY
(TXN)
USING INDEX
INITRANS 2
MAXTRANS 255
PCTFREE 5 TABLESPACE ${USER})
/

--- PROMPT creating index on scn
create index sy$txlog_I1 on sy$txlog(scn)
INITRANS 2
MAXTRANS 255
PCTFREE 10 TABLESPACE ${USER}
/
```

##### 1.2.2.3 创建 sync_core_settings table
```
---PROMPT creating sync core settings table
---PROMPT by setting the value in this table
---PROMPT we can make the databus to be sync/async with insert/updates
create table sync_core_settings (
  raise_dbms_alerts char(1) constraint sync_core_settings_n1 not null
)
INITRANS 1
MAXTRANS 255
PCTUSED 90
PCTFREE 5 TABLESPACE ${USER}
/

insert into sync_core_settings values ('N');
commit;
```

#### 1.2.3 创建procedures

##### 1.2.3.1 创建procedure
```
--- REM this procedure is to compile all the invalid objects in current schema (user)

create or replace procedure compile_allobjects AUTHID CURRENT_USER is
  cursor c1 is select 'alter '||decode(object_type,'PACKAGE BODY','PACKAGE',object_type)||' '||object_name||' '||  decode(object_type,'PACKAGE BODY','COMPILE BODY','COMPILE')
               from user_objects where status='INVALID' order by object_type;
  stmt varchar2(1000);
  begin
    open c1;
    LOOP
      fetch c1 into stmt;
      exit when C1%NOTFOUND;
      begin
        EXECUTE IMMEDIATE stmt ;
      EXCEPTION
        WHEN OTHERS THEN
        dbms_output.put_line('执行错误：----'||to_char(stmt)||'----');
      end;
    end loop;
  end compile_allobjects;
/
```

##### 1.2.3.2 查询packages
```
select 'alter '||decode(object_type,'PACKAGE BODY','PACKAGE',object_type)||' '||object_name||' '||  decode(object_type,'PACKAGE BODY','COMPILE BODY','COMPILE')
   from user_objects where status='INVALID' order by object_type;
select * from user_objects where object_type like 'TRIGGER%';
```

#### 1.2.4 创建 views

##### 1.2.4.1 创建 DB_MODE view
```
--- PROMPT Creating Databus view
create or replace force view DB_MODE
  as
    select cast(open_mode as varchar2(20))open_mode from sys.v_$database
/
```

#### 1.2.5 创建packages

##### 1.2.5.1 创建 sync_core package
```
--- PROMPT creating sync_core package
-- the purpose of this package is to generate the txn and generates the
-- alerts

create or replace package sync_core is

  function getTxn(source in varchar) return number;
  function getScn(v_scn in number, v_ora_rowscn in number) return number;
  function getMask(source in varchar) return number;
  procedure coalesce_log;
  procedure signal_beep(source in varchar);
  procedure unconditional_signal_beep(source in varchar);
end;
/


create or replace package body sync_core is

  infinity constant number := 9999999999999999999999999999;
  type source_bit_map_t is table OF number index by VARCHAR2(30);

  lastTxID varchar2(50);
  currentTxn number;
  currentMask number;

  source_bits source_bit_map_t;

  function getScn(v_scn in number, v_ora_rowscn in number) return number as
    begin
      if(v_scn = infinity) then
        return v_ora_rowscn;
      else
        return v_scn;
      end if;
    end;

  function getMask(source in varchar) return number as
    bitnum number;
    begin
      if not source_bits.exists(source) then
        select bitnum into bitnum from sy$sources where name = source;
        source_bits(source) := power(2,bitnum);
      end if;
      return source_bits(source);
    end;

  -- this is the 'magical' function which creates and returns the Txn (aka
  -- transaction number)
  function getTxn(source in varchar) return number as
    currentTxID varchar2(50);
    mask        number;
    txn_chk    char(1);
    begin
      -- we can get the local transaction id (guaranteed to be unique for the
      -- current transaction)
      currentTxID := DBMS_TRANSACTION.LOCAL_TRANSACTION_ID();
      if currentTxID is null then
        currentTxID := DBMS_TRANSACTION.LOCAL_TRANSACTION_ID(TRUE);
      end if;

      mask := getMask(source);

      -- lastTxID is package scope => may have no value or a value from previous
      -- usage. If value is different it means we are in a new transaction
      if (lastTxID is null or lastTxID <> currentTxID) then
        select sy$scn_seq.nextval into currentTxn from dual;
        currentMask := mask;
        lastTxID := currentTxID;
        insert into sy$txlog(txn,scn,mask) values(currentTxn,infinity,currentMask);
        signal_beep(source);
      else
        begin
          select 'Y' into txn_chk from sy$txlog where txn=currentTxn;
          exception when others then txn_chk :='N';
        end;
        if  txn_chk='N' then
          insert into sy$txlog(txn,scn,mask) values(currentTxn,infinity,currentMask);
          signal_beep(source);
        else
          if (bitand(currentMask,mask) = 0) then
            currentMask := currentMask + mask;
          end if;
          update sy$txlog set mask = currentMask where txn = currentTxn and mask <> currentMask;
          if SQL%ROWCOUNT > 0 then
            signal_beep(source);
          end if;
        end if;
      end if;

      return currentTxn;
    end;

  procedure coalesce_log
  as
    cursor cur_sytxlog_scn is
      select txn, ora_rowscn from sy$txlog where scn=infinity for update;
    sytxlog1 cur_sytxlog_scn%ROWTYPE;
    begin
      open cur_sytxlog_scn;
      LOOP
        fetch cur_sytxlog_scn into sytxlog1;
        exit when cur_sytxlog_scn%NOTFOUND;
        update sy$txlog set scn=sytxlog1.ora_rowscn where scn=infinity and txn=sytxlog1.txn;
      END LOOP;
      close cur_sytxlog_scn;
      commit;
    end;

  procedure signal_beep(source in varchar)
  as
    v_raise_dbms_alerts    char(1);
    begin
      select raise_dbms_alerts into v_raise_dbms_alerts from sync_core_settings;
      if v_raise_dbms_alerts = 'Y' then
        unconditional_signal_beep(source);
      end if;
    end;

  procedure unconditional_signal_beep(source in varchar)
  as
    begin
      --- todo dbms_alert逻辑梳理
      dbms_alert.signal('sy$alert_'||source, 'beep');
      exception when others then
      -- if we get an exception while raising the signal we ignore it
      null;
    end;

end;
/
```

##### 1.2.5.2 创建 sync_alert package
```
--- PROMPT creating sync_alert package
-- the purpose of this package is to allow to register to some alerts and get
-- notified when one is triggered
create or replace package sync_alert as

  function registerSourceWithVersion(source in varchar, version in number) return number;
  procedure unregisterAllSources(source in varchar);
  function waitForEvent(source in varchar, maxWait in number) return varchar;
end sync_alert;
/

create or replace package body sync_alert as

  is_registered boolean := FALSE;

  -- registers a source with a version: after registration, all events occuring on this source
  -- will be returned by waitForEvent
  -- returns null if the source does not exists (source otherwise)
  function registerSourceWithVersion(source in varchar, version in number) return number
  as
    view_name varchar(30);
    bitnum number;
    source_name varchar(100);
    v_mode varchar(15);
    begin
      if version > 0 then
        source_name := source || '_' || version;
      else
        source_name := source;
      end if;
      begin
        select open_mode into v_mode from db_mode;
        exception when others then
        v_mode :=null;
      end;
      begin
        select view_name into view_name from user_views where upper(view_name)=upper('sy$' || source_name);
        exception when no_data_found then
        view_name := null;
      end;
      begin
        select bitnum into bitnum from sy$sources where name=source;
        exception when no_data_found then
        bitnum := null;
      end;
      IF v_mode='READ WRITE'  THEN
        IF not is_registered THEN
          begin
            dbms_alert.register('sy$alert_'||source);
            exception when others then
            null;
          end;
          is_registered := TRUE;
        END IF;
      END IF;
      if (view_name is null or bitnum is null) then
        return null;
      end if;
      return bitnum;
    end;

  -- registers a source: after registration, all events occuring on this source
  -- will be returned by waitForEvent
  -- returns null if the source does not exists (source otherwise)
  --- REM registersource function is removed  because it not used by any application code.

  -- unregisters all sources. After this call, no more events are returned by
  -- waitForEvent
  procedure unregisterAllSources(source in varchar) as
    begin
      IF is_registered THEN
        dbms_alert.remove('sy$alert_'||source);
        is_registered := FALSE;
      END IF;
    end;

  -- wait for an even no longer than the time out (in seconds). Returns the message that
  -- was associated to the event
  function waitForEvent(source in varchar, maxWait in number) return varchar
  as
    msg     varchar2(1800);
    status  number;
    begin
      DBMS_ALERT.WAITONE('sy$alert_'||source, msg, status, maxWait);
      if status = 0 then
        return msg;
      end if;
      return null;
    end;

end sync_alert;
/
```

#### !!!（暂时毋需创建） 1.2.6 创建定时任务
```

--- 更新sy$txlog表 

BEGIN
  DBMS_SCHEDULER.CREATE_PROGRAM(
      program_name=>'P_COALESCE_LOG',
      program_action=>'sync_core.coalesce_log',
      program_type=>'STORED_PROCEDURE',
      number_of_arguments=>0,
      comments=>'New program used to update scn',
      enabled=>TRUE);
  DBMS_SCHEDULER.CREATE_JOB(
      JOB_NAME => 'J_COALESCE_LOG',
      PROGRAM_NAME => 'P_COALESCE_LOG',
      REPEAT_INTERVAL  => 'FREQ=SECONDLY;INTERVAL=2',
      START_DATE => systimestamp at time zone 'Asia/Shanghai',
      COMMENTS => 'this will update the scn on sy$txlog',
      AUTO_DROP => FALSE,
      ENABLED => FALSE);
  DBMS_SCHEDULER.SET_ATTRIBUTE(NAME=>'J_COALESCE_LOG', attribute => 'restartable', value=>TRUE);
  DBMS_SCHEDULER.SET_ATTRIBUTE( NAME =>'J_COALESCE_LOG', attribute =>'logging_level', value => DBMS_SCHEDULER.LOGGING_OFF);
  DBMS_SCHEDULER.ENABLE('J_COALESCE_LOG');
END;
/

--- 发送dbms_alert
BEGIN
  dbms_scheduler.create_job(
      job_name => 'J_CALL_SIGNAL',
      job_type => 'PLSQL_BLOCK',
      job_action => 'begin
   sync_core.unconditional_signal_beep;
end;',
      repeat_interval => 'FREQ=SECONDLY',
      start_date =>  systimestamp at time zone 'Asia/Shanghai',
      job_class => 'DEFAULT_JOB_CLASS',
      comments => 'Call sync_core.unconditional_signal_beep to signal that databus events MAY be available',
      auto_drop => FALSE,
      enabled => FALSE);
  sys.dbms_scheduler.set_attribute( name => 'J_CALL_SIGNAL', attribute => 'job_priority', value => 1);
  sys.dbms_scheduler.set_attribute( name => 'J_CALL_SIGNAL', attribute => 'logging_level', value => DBMS_SCHEDULER.LOGGING_OFF);
  sys.dbms_scheduler.set_attribute( name => 'J_CALL_SIGNAL', attribute => 'job_weight', value => 1);
  sys.dbms_scheduler.disable( 'J_CALL_SIGNAL' );
END;
/
```

### 1.3 订阅table的相关处理
订阅表的insert、update、delete触发器，订阅表的视图、订阅表的临时表、订阅表临时表的视图。如：要订阅rft_account

**注意：** 订阅表要添加txn字段

#### 1.3.1 创建订阅表临时表
**注意：**创建临时表时在订阅表的基础上添加OP_CODE字段。
```
create table ACC_ACCOUNT_TEMP
(
  ID                NUMBER,
  ACCOUNT           VARCHAR2(50 char),
  SNO               VARCHAR2(50 char),
  BALANCE           NUMBER     default 0.00,
  DELSTATUS       NUMBER(10),
  TXN               NUMBER,
  OP_CODE           NUMBER(2)
)
/
```

#### 1.3.2 创建订阅表的insert、update、delete触发器
```
CREATE OR REPLACE TRIGGER ACC_ACCOUNT_TRG
  before
  insert or update or delete
  on ACC_ACCOUNT
  for each row
  declare
    CURRENT_ID number;
    TEMP_TXN number;
    OP_CODE number;
  begin
    OP_CODE := 0;
    if (deleting) then
      OP_CODE := 1;
      TEMP_TXN := :old.TXN;
    elsif ((updating('BALANCE') or updating('DELSTATUS')) and :new.txn < 0) then
      :new.txn := -:new.txn;
      TEMP_TXN := :new.TXN;
    elsif (updating('BALANCE') or updating('DELSTATUS') or inserting) then
      :new.txn := sync_core.getTxn('acc_account');
      TEMP_TXN := :new.TXN;
    end if;

    select SY$ID_SEQ.nextval into CURRENT_ID from dual;
    if (inserting) then
      insert into ACC_ACCOUNT_TEMP (TXN, OP_CODE)
      values (TEMP_TXN, OP_CODE);
    elsif (updating('BALANCE') or updating('DELSTATUS') or deleting) then
      insert into ACC_ACCOUNT_TEMP (ID, ACCOUNT, SNO, BALANCE, TXN, OP_CODE)
      values (CURRENT_ID, :old.ACCOUNT, :old.SNO, :old.BALANCE, TEMP_TXN, OP_CODE);
    end if;
  end;
/
```

#### 1.3.3 订阅表的视图
```
CREATE OR REPLACE FORCE VIEW sy$acc_account
  AS
    SELECT
           txn,
           ID key,
           ACCOUNT,
           SNO,
           DELSTATUS,
           BALANCE
    FROM
         acc_account;
/
```

#### 1.3.4 订阅表临时表的视图
**注意：** 临时表视图中的字段要设置有加"_1"后缀的别名，OP_CODE除外。

```
CREATE OR REPLACE FORCE VIEW sy$acc_account_temp
  AS
    SELECT
           txn txn_1,
           ID key_1,
           ACCOUNT ACCOUNT_1,
           SNO SNO_1,
           BALANCE BALANCE_1,
           DELSTATUS DELSTATUS_1,
           OP_CODE
    FROM
         acc_account_temp;
/
```

#### 1.3.5 插入sources
```
insert into sy$sources values('acc_account',0);
commit;
```

### 1.4 编译 packages
```
begin
  compile_allobjects;
end;
```

### 1.5 配置相关修改
#### 1.5.1 创建schemas文件
##### 1.5.1.1
如：
```
{
  "name" : "AccAccount_V1",
  "doc" : "Avro schema for sy$acc_account",
  "type" : "record",
  "meta" : "dbFieldName=sy$acc_account;pk=key;",
  "namespace" : "com.ronfton.dbus.client.entity",
  "fields" : [ {
    "name" : "txn",
    "type" : [ "long", "null" ],
    "meta" : "dbFieldName=TXN;dbFieldPosition=0;"
  }, {
    "name" : "key",
    "type" : [ "long", "null" ],
    "meta" : "dbFieldName=KEY;dbFieldPosition=1;"
  }, {
    "name" : "account",
    "type" : [ "string", "null" ],
    "meta" : "dbFieldName=ACCOUNT;dbFieldPosition=2;"
  }, {
    "name" : "sno",
    "type" : [ "string", "null" ],
    "meta" : "dbFieldName=SNO;dbFieldPosition=3;"
  }, {
    "name" : "balance",
    "type" : [ "double", "null" ],
    "meta" : "dbFieldName=BALANCE;dbFieldPosition=4;"
  }]
}
```
**注意：** 文件中的meta为订阅表视图名称

**约定：** 
1. 上述文件的命名规则一般按照：_订阅表对应实体路径_ + ".1.avsc"
2. 文件中的namespace为 _订阅表对应实体包路径_

##### 1.5.1.2 
将上述创建的schemas文件名添加至`index.schemas_registry`文件中 

#### 1.5.2 配置订阅源（sources-oracle.json）
订阅源配置文件结构如下：

```
{
    "name" : "rx",
    "id"  : 1,
    "uri" : "jdbc:oracle:thin:{USER}/{PASSWORD}@xxx.xxx.xxx.xxx:1521:orcl",
	"slowSourceQueryThreshold" : 2000,
	"sources" :
	[
		{
			"id" : 102,
			"name" : "订阅源名称（如：com.ronfton.dbus.client.entity.AccAccount）",
			"uri": "{USER}.{TABLE}（如：rx.acc_account）",
			"partitionFunction" : "constant:1"
		}
	]
}
```

其中：
1. sources：订阅源
2. sources.id：订阅源名称
3. uri：数据库（USER）+ 表名

#### 1.5.3 配置relay服务（relay-oracle.properties）
略


## 注意：要订阅多张表需要执行上述1.3，1.5.1~1.5.2 里面的相关操作