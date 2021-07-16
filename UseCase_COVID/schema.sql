DROP TABLE IF EXISTS schema;
CREATE TABLE schema (
	tablename text NULL,
	fieldname text NULL,
	"key" bool NULL,
	varfree bool NULL DEFAULT false
);

