create or replace schema localdb.sys_root;
set schema 'localdb.sys_root';
set path 'localdb.sys_root';

-- FIXME jvs 17-Sept-2006:  None of these views should be granted
-- to public; they're for the DBA only.  Full security will involve
-- corresponding user_ views which show just the subset accessible
-- to CURRENT_USER

create or replace function convert_nullable_int_to_boolean(n integer)
returns boolean
contains sql
deterministic
return case
  when n=0 then false
  when n=1 then true
  else unknown
end;

create or replace view dba_schemas as
  select
    catalog_name,
    schema_name,
    cast(ai."name" as varchar(128)) as creator,
    creation_timestamp,
    last_modified_timestamp,
    remarks,
    cast(si."mofId" as varchar(128)) as mof_id,
    cast(si."lineageId" as varchar(128)) as lineage_id
  from
    sys_boot.mgmt.dba_schemas_internal2 si
  inner join
    sys_fem."Security"."AuthId" ai
  on
    si."Grantee" = ai."mofId"
;

grant select on dba_schemas to public;

create or replace view dba_tables as
  select
    catalog_name,
    schema_name,
    table_name,
    table_type,
    cast(ai."name" as varchar(128)) as creator,
    creation_timestamp,
    last_modification_timestamp as last_modified_timestamp,
    remarks,
    cast(dti."mofId" as varchar(128)) as mof_id,
    cast(dti."lineageId" as varchar(128)) as lineage_id
  from
    sys_boot.mgmt.dba_tables_internal2 dti
  inner join
    sys_fem."Security"."AuthId" ai
  on
    dti."Grantee" = ai."mofId"
;

grant select on dba_tables to public;

create or replace view dba_columns as
  select
    cast(table_cat as varchar(128)) as catalog_name,
    cast(table_schem as varchar(128)) as schema_name,
    cast(table_name as varchar(128)) as table_name,
    cast(column_name as varchar(128)) as column_name,
    ordinal_position,
    dt."name" as datatype,
    column_size as "PRECISION",
    dec_digits,
    sys_root.convert_nullable_int_to_boolean(nullable) as is_nullable,
    remarks,
    cast(ci."mofId" as varchar(128)) as mof_id,
    cast(ci."lineageId" as varchar(128)) as lineage_id
  from
    sys_boot.jdbc_metadata.columns_view_internal ci
  inner join
    sys_cwm."Relational"."SQLDataType" dt
  on
    ci."type" = dt."mofId"
;

grant select on dba_columns to public;

create or replace view dba_column_stats as
  select
    cast(table_cat as varchar(128)) as catalog_name,
    cast(table_schem as varchar(128)) as schema_name,
    cast(table_name as varchar(128)) as table_name,
    cast(column_name as varchar(128)) as column_name,
    "CARDINALITY" as distinct_value_count,
    cardinality_estimated as is_distinct_value_count_estimated,
    percent_sampled,
    sample_size,
    bar_count,
    rows_per_bar,
    rows_last_bar,
    last_analyze_time
  from
    sys_boot.mgmt.histograms_view_internal
;

grant select on dba_column_stats to public;

create or replace view dba_column_histograms as
  select
    cast(table_cat as varchar(128)) as catalog_name,
    cast(table_schem as varchar(128)) as schema_name,
    cast(table_name as varchar(128)) as table_name,
    cast(column_name as varchar(128)) as column_name,
    ordinal,
    cast(start_value as varchar(128)) as start_value,
    value_count
  from
    sys_boot.mgmt.histogram_bars_view
;

grant select on dba_column_histograms to public;

create or replace view dba_views as
  select
    catalog_name,
    schema_name,
    view_name,
    cast(ai."name" as varchar(128)) as creator,
    creation_timestamp,
    last_modification_timestamp as last_modified_timestamp,
    original_text,
    remarks,
    cast(vi."mofId" as varchar(128)) as mof_id,
    cast(vi."lineageId" as varchar(128)) as lineage_id
  from
    sys_boot.mgmt.dba_views_internal2 vi
  inner join
    sys_fem."Security"."AuthId" ai
  on
    vi."Grantee" = ai."mofId"
;

grant select on dba_views to public;

create or replace view dba_stored_tables as
  select
    catalog_name,
    schema_name,
    table_name,
    cast(ai."name" as varchar(128)) as creator,
    creation_timestamp,
    last_modification_timestamp as last_modified_timestamp,
    last_analyze_row_count,
    last_analyze_timestamp,
    current_row_count,
    deleted_row_count,
    remarks,
    cast(sti."mofId" as varchar(128)) as mof_id,
    cast(sti."lineageId" as varchar(128))as lineage_id
  from
    sys_boot.mgmt.dba_stored_tables_internal2 sti
  inner join
    sys_fem."Security"."AuthId" ai
  on
    sti."Grantee" = ai."mofId"
;

grant select on dba_stored_tables to public;

create or replace view dba_unclustered_indexes as
    select
        i.catalog_name,
        i.schema_name,
        i.index_name,
        i.creator,
        i.creation_timestamp,
        i.table_name,
        cast(ic.concatenated_child_values as varchar(4096))
            as index_column_names,
        i.is_unique,
        i.remarks,
        i.mof_id,
        i.lineage_id
    from
        sys_boot.mgmt.dba_indexes_internal i
    inner join
        table(applib.collapse_rows(cursor(
            select mof_id, column_name
                from sys_boot.mgmt.dba_index_columns_internal
                order by mof_id, ordinal_position),
            ',')) ic
    on
        i.mof_id = ic.parent_value
    where
        i.is_clustered is false
;

grant select on dba_unclustered_indexes to public;

create or replace view dba_routines as
  select
    catalog_name,
    schema_name,
    invocation_name,
    specific_name,
    external_name,
    routine_type,
    cast(ai."name" as varchar(128)) as creator,
    creation_timestamp,
    last_modified_timestamp,
    is_table_function,
    parameter_style,
    is_deterministic,
    data_access,
    remarks,
    cast(ri."mofId" as varchar(128)) as mof_id,
    cast(ri."lineageId" as varchar(128)) as lineage_id
  from
    sys_boot.mgmt.dba_routines_internal2 ri
  inner join
    sys_fem."Security"."AuthId" ai
  on
    ri."Grantee" = ai."mofId"
;

grant select on dba_routines to public;

create or replace view dba_functions as
  select 
    * 
  from sys_root.dba_routines
  where routine_type='FUNCTION'
;
    
grant select on dba_functions to public;

create or replace view dba_routine_parameters as
  select
    catalog_name,
    schema_name,
    routine_specific_name,
    parameter_name,
    (ordinal + 1) as ordinal_position,
    cast(dt."name" as varchar(128)) as datatype,
    "PRECISION",
    dec_digits,
    remarks,
    cast(rpi."mofId" as varchar(128)) as mof_id,
    cast(rpi."lineageId" as varchar(128)) as lineage_id
  from
    sys_boot.mgmt.dba_routine_parameters_internal1 rpi
  inner join
    sys_cwm."Relational"."SQLDataType" dt
  on
    rpi."type" = dt."mofId"
  where parameter_name <> 'RETURN'
;

grant select on dba_routine_parameters to public;

create or replace view dba_function_parameters as
  select
    catalog_name,
    schema_name,
    routine_specific_name,
    parameter_name,
    (ordinal + 1) as ordinal_position,
    case 
      when rpi.is_table_function and parameter_name='RETURN' 
        then cast('TABLE' as varchar(128))
      else cast(dt."name" as varchar(128)) 
      end as datatype,
    cast(dt."name" as varchar(128)) as datatype,
    "PRECISION",
    dec_digits,
    remarks,
    cast(rpi."mofId" as varchar(128)) as mof_id,
    cast(rpi."lineageId" as varchar(128)) as lineage_id
  from
    sys_boot.mgmt.dba_routine_parameters_internal1 rpi
  inner join
    sys_cwm."Relational"."SQLDataType" dt
  on
    rpi."type" = dt."mofId"
  where routine_type = 'FUNCTION'
;

grant select on dba_function_parameters to public;

create or replace view dba_foreign_wrappers as
  select
    foreign_wrapper_name,
    library,
    "LANGUAGE",
    cast(ai."name" as varchar(128)) as creator,
    creation_timestamp,
    last_modified_timestamp,
    remarks,
    cast(fwi."mofId" as varchar(128)) as mof_id,
    cast(fwi."lineageId" as varchar(128)) as lineage_id
  from
    sys_boot.mgmt.dba_foreign_wrappers_internal fwi
  inner join
    sys_fem."Security"."AuthId" ai
  on
    fwi."Grantee" = ai."mofId"
;

grant select on dba_foreign_wrappers to public;

create or replace view dba_foreign_wrapper_options as 
  select
    cast(dw."name" as varchar(128)) as foreign_wrapper_name,
    cast(so."name" as varchar(128)) as option_name,
    cast(so."value" as varchar(128)) as option_value,
    cast(so."mofId" as varchar(128)) as mof_id
  from
    sys_fem.med."DataWrapper" dw
  inner join
    sys_fem.med."StorageOption" so
  on
    dw."mofId" = so."StoredElement"
  where 
    dw."foreign" = true
;

grant select on dba_foreign_wrapper_options to public;

create or replace view dba_foreign_servers as
  select
    fsi.foreign_wrapper_name,
    fsi.foreign_server_name,
    cast(ai."name" as varchar(128)) as creator,
    fsi.creation_timestamp,
    fsi.last_modified_timestamp,
    fsi.remarks,
    cast(fsi."mofId" as varchar(128)) as mof_id,
    cast(fsi."lineageId" as varchar(128)) as lineage_id,
    fsi."mofId" as mofid,
    fsi."lineageId" as lineageid
  from
    sys_boot.mgmt.dba_foreign_servers_internal2 fsi
  inner join
    sys_fem."Security"."AuthId" ai
  on
    fsi."Grantee" = ai."mofId"
;

grant select on dba_foreign_servers to public;

create or replace view dba_foreign_server_options as
  select
    foreign_wrapper_name,
    foreign_server_name,
    cast(so."name" as varchar(65535)) as option_name,
    cast(so."value" as varchar(65535)) as option_value,
    cast(so."mofId" as varchar(128)) as mof_id,
    so."mofId" as mofid
  from
    sys_boot.mgmt.dba_foreign_servers_internal1 fsi
  inner join
    sys_fem.med."StorageOption" so
  on
    fsi."mofId" = so."StoredElement"
;

grant select on dba_foreign_server_options to public;

create or replace view dba_foreign_tables as
  select
    fti.foreign_wrapper_name,
    fti.foreign_server_name,
    fti.foreign_table_name,
    cast(ai."name" as varchar(128)) as creator,
    fti.creation_timestamp,
    fti.last_modified_timestamp,
    fti.last_analyze_row_count,
    fti.last_analyze_timestamp,
    fti.remarks,
    cast(fti."mofId" as varchar(128)) as mof_id,
    cast(fti."lineageId" as varchar(128)) as lineage_id
  from
    sys_boot.mgmt.dba_foreign_tables_internal2 fti
  inner join
    sys_fem."Security"."AuthId" ai
  on
    fti."Grantee" = ai."mofId"
;

grant select on dba_foreign_tables to public;

create or replace view dba_foreign_table_options as
  select
    foreign_wrapper_name,
    foreign_server_name,
    foreign_table_name,
    cast(so."name" as varchar(65535)) as option_name,
    cast(so."value" as varchar(65535)) as option_value,
    cast(so."mofId" as varchar(128)) as mof_id
  from
    sys_boot.mgmt.dba_foreign_tables_internal1 fti
  inner join
    sys_fem.med."StorageOption" so
  on
    fti."mofId" = so."StoredElement"
;

grant select on dba_foreign_table_options to public;

create or replace view dba_system_parameters as
select col_name as param_name, col_value as param_value from 
((select * from table(
  applib.pivot_columns_to_rows(
    cursor(select * from sys_fem."Config"."FarragoConfig"))))
union all
(select * from table(
  applib.pivot_columns_to_rows(
    cursor(select * from sys_fem."Config"."FennelConfig")))))
where col_name not in 
('mofId', 'mofClassName', 'FarragoConfig', 'FennelConfig', 'name')
;

grant select on dba_system_parameters to public;

create or replace view dba_sessions as
select 
id as session_id,
url as connect_url,
current_user_name,
current_role_name,
session_user_name,
system_user_name,
system_user_fullname,
session_name,
program_name as client_program_name,
process_id as client_process_id,
catalog_name as current_catalog_name,
schema_name as current_schema_name,
is_closed,
is_auto_commit,
is_txn_in_progress,
label_name as current_label_name
from sys_boot.mgmt.sessions_view;

grant select on dba_sessions to public;

create or replace view dba_sql_statements as
select
id as stmt_id,
session_id,
sql_stmt as sql_text,
create_time as creation_timestamp,
parameters as parameter_values
from sys_boot.mgmt.statements_view;

grant select on dba_sql_statements to public;

create or replace view dba_repository_properties as
select * from sys_boot.mgmt.repository_properties_view;

grant select on dba_repository_properties to public;

create or replace view dba_repository_integrity_violations as
select * from table(sys_boot.mgmt.repository_integrity_violations());

grant select on dba_repository_integrity_violations to public;

create or replace view dba_objects_in_use as
select * from sys_boot.mgmt.objects_in_use_view;

grant select on dba_objects_in_use to public;

create or replace view dba_threads
as select * from table(sys_boot.mgmt.threads());

grant select on dba_threads to public;

create or replace view dba_thread_stack_entries
as select * from table(sys_boot.mgmt.thread_stack_entries());

grant select on dba_thread_stack_entries to public;

create or replace view dba_performance_counters
as select * from table(sys_boot.mgmt.performance_counters());

grant select on dba_performance_counters to public;

create or replace view dba_system_info
as 
(select * from table(sys_boot.mgmt.system_info()))
union all
(select * from dba_performance_counters);

grant select on dba_system_info to public;

-- NOTE jvs 17-Sept-2006:  This view is intentionally NOT prefixed
-- with dba_ because it shows information about the current session only

create or replace view user_session_parameters as
select * from sys_boot.mgmt.session_parameters_view;

grant select on user_session_parameters to public;

create or replace view dba_labels as
  select
    lbl.label_name,
    lbl.parent_label_name,
    lbl.csn,
    cast(ai."name" as varchar(128)) as creator,
    lbl.creation_timestamp,
    lbl.last_modified_timestamp,
    lbl.remarks,
    cast(lbl."mofId" as varchar(128)) as mof_id,
    cast(lbl."lineageId" as varchar(128)) as lineage_id
  from
    sys_boot.mgmt.dba_labels_internal as lbl
  inner join
    sys_fem."Security"."AuthId" ai
  on
    lbl."Grantee" = ai."mofId"
;

grant select on dba_labels to public;

create or replace view dba_system_backups as
  select
    cast("type" as varchar(128)) as backup_type,
    "commitSequenceNumber" as csn,
    cast("startTimestamp" as timestamp) as start_timestamp,
    cast("endTimestamp" as timestamp) as end_timestamp,
    cast("status" as varchar(128)) as status,
    cast("mofId" as varchar(128)) as mof_id
  from sys_fem.med."SystemBackup";

-- Flush all entries from the global code cache
create or replace procedure flush_code_cache()
  language java
  parameter style java
  reads sql data
  external name 
  'class net.sf.farrago.syslib.FarragoManagementUDR.flushCodeCache';

-- Kill a session by its ID (see dba_sessions)
create or replace procedure kill_session(in id bigint)
language java
parameter style java
no sql
external name 'class net.sf.farrago.syslib.FarragoKillUDR.killSession';

create or replace procedure kill_session(in id bigint, in cancel_only boolean)
language java
parameter style java
no sql
specific kill_session_cancel
external name 'class net.sf.farrago.syslib.FarragoKillUDR.killSession';

-- Kill a statement by its ID (see dba_sql_statements)
create or replace procedure kill_statement(in id bigint)
language java
parameter style java
no sql
external name 'class net.sf.farrago.syslib.FarragoKillUDR.killStatement';

create or replace procedure kill_statement(in id bigint, in cancel_only boolean)
language java
parameter style java
no sql
specific kill_statement_cancel
external name 'class net.sf.farrago.syslib.FarragoKillUDR.killStatement';

-- Kill all statements whose sql_stmt text contains the input string
-- (similar to Unix killall)
create or replace procedure kill_all_matching_statements(in s varchar(256))
language java
parameter style java
no sql
external name 'class net.sf.farrago.syslib.FarragoKillUDR.killStatementMatch';

create or replace procedure kill_all_matching_statements(
    in s varchar(256), in cancel_only boolean)
language java
parameter style java
no sql
specific kill_all_matching_statements_cancel
external name 'class net.sf.farrago.syslib.FarragoKillUDR.killStatementMatch';

-- retrieves a long catalog string attribute in chunks
create or replace function retrieve_repository_lob_text(
  mof_id varchar(128),
  attribute_name varchar(128))
returns table(
  chunk_offset integer,
  chunk_text varchar(1024))
language java
parameter style system defined java
no sql
external name 
'class net.sf.farrago.syslib.FarragoManagementUDR.lobText';

-- Exports the complete contents of the catalog to an XMI file
create or replace procedure export_catalog_xmi(in filename varchar(65535))
language java
parameter style java
no sql
external name 'class net.sf.farrago.syslib.FarragoManagementUDR.exportCatalog';

-- Export schema to file UDP, field delimiter and file extension can be 
-- specified by user
create or replace procedure export_schema_to_file(
  in cat varchar(128),
  in schma varchar(128),
  in exclude boolean, 
  in tlist varchar(65535),
  in tpattern varchar(65535),
  in dir varchar(65535),
  in bcp boolean,
  in delete_failed_file boolean,
  in field_delimiter varchar(2),
  in file_extension varchar(5)) 
language java
reads sql data
specific export_schema_to_file
called on null input
external name 'class net.sf.farrago.syslib.FarragoExportSchemaUDR.exportSchemaToFile';

-- Export schema to file UDP with field delimiter, file extention and
-- datetime formats
create or replace procedure export_schema_to_file(
  in cat varchar(128),
  in schma varchar(128),
  in exclude boolean, 
  in tlist varchar(65535),
  in tpattern varchar(65535),
  in dir varchar(65535),
  in bcp boolean,
  in delete_failed_file boolean,
  in field_delimiter varchar(2),
  in file_extension varchar(5),
  in date_format varchar(50),
  in time_format varchar(50),
  in timestamp_format varchar(50))
language java
reads sql data
specific export_schema_to_file_2
called on null input
external name 'class net.sf.farrago.syslib.FarragoExportSchemaUDR.exportSchemaToFile';


-- Export schema to csv files UDP. Standard version always creates bcp files
-- and deletes incomplete files for a failed table export. 
create or replace procedure export_schema_to_csv(
  in cat varchar(128),
  in schma varchar(128),
  in exclude boolean, 
  in tlist varchar(65535),
  in tpattern varchar(65535),
  in dir varchar(65535),
  in bcp boolean,
  in delete_failed_file boolean)
language java
reads sql data
specific export_schema_with_options
called on null input
external name 'class net.sf.farrago.syslib.FarragoExportSchemaUDR.exportSchemaToCsv';

create or replace procedure export_schema_to_csv(
  in cat varchar(128),
  in schma varchar(128),
  in exclude boolean, 
  in tlist varchar(65535),
  in tpattern varchar(65535),
  in dir varchar(65535)) 
language java
reads sql data
specific export_schema_standard
called on null input
external name 'class net.sf.farrago.syslib.FarragoExportSchemaUDR.exportSchemaToCsv';

-- Export foreign schema to csv files UDP. Standard version always creates bcp
-- files and deletes incomplete files for a failed table export.
create or replace procedure export_foreign_schema_to_csv(
  in serv varchar(128),
  in fschema varchar(128),
  in exclude boolean,
  in tlist varchar(65535),
  in tpattern varchar(65535),
  in dir varchar(65535),
  in bcp boolean,
  in delete_failed_file boolean)
language java
modifies sql data
specific export_foreign_schema_with_options
called on null input
external name 'class net.sf.farrago.syslib.FarragoExportSchemaUDR.exportForeignSchemaToCsv';

create or replace procedure export_foreign_schema_to_csv(
  in serv varchar(128),
  in fschema varchar(128),
  in exclude boolean,
  in tlist varchar(65535),
  in tpattern varchar(65535),
  in dir varchar(65535))
language java
modifies sql data
specific export_foreign_schema_standard
called on null input
external name 'class net.sf.farrago.syslib.FarragoExportSchemaUDR.exportForeignSchemaToCsv';

-- Incremental export for local schema, gets rows where 
-- last_mod_col > last_mod_ts.  Standard version always creates bcp files and
-- deletes incomplete files for a failed table export.
create or replace procedure export_schema_incremental_to_csv(
  in cat varchar(128),
  in schma varchar(128),
  in exclude boolean, 
  in tlist varchar(65535),
  in tpattern varchar(65535),
  in last_mod_ts timestamp,
  in last_mod_col varchar(128),
  in dir varchar(65535),
  in bcp boolean,
  in delete_failed_file boolean) 
language java
reads sql data
specific export_schema_incremental_with_options
called on null input
external name 'class net.sf.farrago.syslib.FarragoExportSchemaUDR.exportSchemaIncrementalToCsv';

create or replace procedure export_schema_incremental_to_csv(
  in cat varchar(128),
  in schma varchar(128),
  in exclude boolean, 
  in tlist varchar(65535),
  in tpattern varchar(65535),
  in last_mod_ts timestamp,
  in last_mod_col varchar(128),
  in dir varchar(65535)) 
language java
reads sql data
specific export_schema_incremental_standard
called on null input
external name 'class net.sf.farrago.syslib.FarragoExportSchemaUDR.exportSchemaIncrementalToCsv';

-- Incremental export for foreign schema, gets rows where 
-- last_mod_col > last_mod_ts.  Standard version always creates bcp files and
-- deletes incomplete files for a failed table export.
create or replace procedure export_foreign_schema_incremental_to_csv(
  in serv varchar(128),
  in fschema varchar(128),
  in exclude boolean,
  in tlist varchar(65535),
  in tpattern varchar(65535),
  in last_mod_ts timestamp,
  in last_mod_col varchar(128),
  in dir varchar(65535),
  in bcp boolean,
  in delete_failed_file boolean)
language java
modifies sql data
specific export_foreign_schema_incremental_with_options
called on null input
external name 'class net.sf.farrago.syslib.FarragoExportSchemaUDR.exportForeignSchemaIncrementalToCsv';

create or replace procedure export_foreign_schema_incremental_to_csv(
  in serv varchar(128),
  in fschema varchar(128),
  in exclude boolean,
  in tlist varchar(65535),
  in tpattern varchar(65535),
  in last_mod_ts timestamp,
  in last_mod_col varchar(128),
  in dir varchar(65535))
language java
modifies sql data
specific export_foreign_schema_incremental_standard
called on null input
external name 'class net.sf.farrago.syslib.FarragoExportSchemaUDR.exportForeignSchemaIncrementalToCsv';

-- Export csv files for tables combined from two schemas.  The tables in the 
-- two schemas must have the same structure. Data from the original schema 
-- which has been deleted will not be seen, only updates and new records 
-- from the incremental schema.  Standard version always creates bcp files and
-- deletes incomplete files for a failed table export.
create or replace procedure export_merged_schemas_to_csv(
  in orig_catalog varchar(128),
  in orig_schema varchar(128),
  in incr_catalog varchar(128),
  in incr_schema varchar(128),
  in exclude boolean,
  in table_list varchar(65535),
  in table_pattern varchar(65535),
  in id_column varchar(128),
  in dir varchar(65535),
  in bcp boolean,
  in delete_failed_file boolean)
language java
contains sql
specific export_merged_schema_with_options
called on null input
external name 'class net.sf.farrago.syslib.FarragoExportSchemaUDR.exportMergedSchemas';

create or replace procedure export_merged_schemas_to_csv(
  in orig_catalog varchar(128),
  in orig_schema varchar(128),
  in incr_catalog varchar(128),
  in incr_schema varchar(128),
  in exclude boolean,
  in table_list varchar(65535),
  in table_pattern varchar(65535),
  in id_column varchar(128),
  in dir varchar(65535))
language java
contains sql
specific export_merged_schema_standard
called on null input
external name 'class net.sf.farrago.syslib.FarragoExportSchemaUDR.exportMergedSchemas';

-- Export result of a single query to a file
-- NOTE:  query must be quoted as a string literal (TODO:  UDX or
-- support for cursor parameter to procedures)
create or replace procedure export_query_to_file(
  in query_sql varchar(65535),
  in path_without_extension varchar(65535),
  in bcp boolean,
  in delete_failed_file boolean)
language java
reads sql data
called on null input
external name 'class net.sf.farrago.syslib.FarragoExportSchemaUDR.exportQueryToFile';

-- Export result of a single query to a file, with control over field
-- delimiter, datafile extension, and date/time formatting
create or replace procedure export_query_to_file(
  in query_sql varchar(65535),
  in path_without_extension varchar(65535),
  in bcp boolean,
  in delete_failed_file boolean,
  in field_delimiter varchar(2),
  in file_extension varchar(5),
  in date_format varchar(128),
  in time_format varchar(128),
  in timestamp_format varchar(128))
language java
reads sql data
specific export_query_to_file_2
called on null input
external name 'class net.sf.farrago.syslib.FarragoExportSchemaUDR.exportQueryToFile';

-- Export result of a single query to a file, with control over field
-- delimiter, datafile extension, date/time formatting, and whether
-- to even export data at all
create or replace procedure export_query_to_file(
  in query_sql varchar(65535),
  in path_without_extension varchar(65535),
  in bcp boolean,
  in include_data boolean,
  in delete_failed_file boolean,
  in field_delimiter varchar(2),
  in file_extension varchar(5),
  in date_format varchar(128),
  in time_format varchar(128),
  in timestamp_format varchar(128))
language java
reads sql data
specific export_query_to_file_3
called on null input
external name 'class net.sf.farrago.syslib.FarragoExportSchemaUDR.exportQueryToFile';

-- Tests that a connection can be established to a particular
-- SQL/MED local or foreign data server.
create or replace procedure test_data_server(
  server_name varchar(128))
  language java
  parameter style java
  no sql
  external name 
  'class net.sf.farrago.syslib.FarragoMedUDR.testServer';

-- Tests that a connection can be established for all SQL/MED servers
-- instantiated from a particular data wrapper.
create or replace procedure test_all_servers_for_wrapper(
  wrapper_name varchar(128))
  language java
  parameter style java
  no sql
  external name 
  'class net.sf.farrago.syslib.FarragoMedUDR.testAllServersForWrapper';

create or replace procedure backup_database(
    in archive_directory varchar(65535),
    in backup_type varchar(16),
    in compression_mode varchar(16))
language java
parameter style java
no sql
external name
'class net.sf.farrago.syslib.FarragoManagementUDR.backupDatabaseWithoutSpaceCheck';

create or replace procedure backup_database_if_space_available(
    in archive_directory varchar(65535),
    in backup_type varchar(16),
    in compression_mode varchar(16),
    in padding_byte_count bigint)
language java
parameter style java
no sql
external name
'class net.sf.farrago.syslib.FarragoManagementUDR.backupDatabaseWithSpaceCheck';

create or replace procedure restore_database(
    in archive_directory varchar(65535))
language java
parameter style java
no sql
external name 'class net.sf.farrago.syslib.FarragoManagementUDR.restoreDatabase';

create or replace procedure restore_database_without_catalog(
    in archive_directory varchar(65535))
language java
parameter style java
no sql
external name 'class net.sf.farrago.syslib.FarragoManagementUDR.restoreDatabaseWithoutCatalog';

create or replace procedure change_default_character_set_to_unicode()
language java
no sql
external name 'class net.sf.farrago.syslib.FarragoManagementUDR.setUnicodeAsDefault';

create or replace function get_current_sessionid()
returns integer
language java
no sql
external name 'class net.sf.farrago.syslib.FarragoManagementUDR.getCurrentSessionId';
