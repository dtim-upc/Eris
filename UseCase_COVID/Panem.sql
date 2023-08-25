DROP TABLE IF EXISTS REPORTED CASCADE;	
create table Reported (
	district TEXT, 
	year TEXT, 
	cases FLOAT,
	primary key (district, year)
	);
delete from schema where tablename='REPORTED';
insert into schema (tablename, fieldname, key, varfree) values ('REPORTED', 'DISTRICT', true, false);
insert into schema (tablename, fieldname, key, varfree) values ('REPORTED', 'YEAR', true, false);
insert into schema (tablename, fieldname, key, varfree) values ('REPORTED', 'CASES', false, false);

DROP TABLE IF EXISTS AggReported CASCADE;
create table AggReported (
	year TEXT, 
	cases FLOAT,
	primary key (year)
	);
delete from schema where tablename='AGGREPORTED';
insert into schema (tablename, fieldname, key, varfree) values ('AGGREPORTED', 'YEAR', true, false);
insert into schema (tablename, fieldname, key, varfree) values ('AGGREPORTED', 'CASES', false, false);

DROP TABLE IF EXISTS Benznidazol CASCADE;
create table Benznidazol (
	year text,
	bottles FLOAT,
	primary key (year)
	);
delete from schema where tablename='BENZNIDAZOL';
insert into schema (tablename, fieldname, key, varfree) values ('BENZNIDAZOL', 'YEAR', true, false);
insert into schema (tablename, fieldname, key, varfree) values ('BENZNIDAZOL', 'BOTTLES', false, false);

DROP TABLE IF EXISTS Nifurtimox CASCADE;
create table Nifurtimox(
	year text, 
	tablets FLOAT,
	primary key (year)
	);
delete from schema where tablename='NIFURTIMOX';
insert into schema (tablename, fieldname, key, varfree) values ('NIFURTIMOX', 'YEAR', true, false);
insert into schema (tablename, fieldname, key, varfree) values ('NIFURTIMOX', 'TABLETS', false, false);

insert into Reported (district, year, cases) values ('I','2020',10);
insert into Reported (district, year, cases) values ('II','2020',10);
insert into Reported (district, year, cases) values ('III','2020',10);
insert into Reported (district, year, cases) values ('IV','2020',10);
insert into Reported (district, year, cases) values ('V','2020',10);
insert into Reported (district, year, cases) values ('VI','2020',10);
insert into Reported (district, year, cases) values ('VII','2020',10);
insert into Reported (district, year, cases) values ('VIII','2020',10);
insert into Reported (district, year, cases) values ('IX','2020',10);
insert into Reported (district, year, cases) values ('X','2020',10);
insert into Reported (district, year, cases) values ('XI','2020',10);
insert into Reported (district, year, cases) values ('XII','2020',10);
insert into Reported (district, year, cases) values ('XIII','2020',0);
insert into AggReported (year, cases) values ('2020',130);
insert into Benznidazol (year, bottles) values ('2020',200);
insert into Nifurtimox (year, tablets) values ('2020',15000);
