DROP TABLE IF EXISTS REPORTED CASCADE;	
create table Reported (
	district TEXT, 
	year TEXT, 
	cases FLOAT,
	primary key (district, year)
	);

DROP TABLE IF EXISTS AggReported_Primitive CASCADE;
create table AggReported_Primitive (
	year TEXT, 
	cases FLOAT,
	primary key (year)
	);

drop view if exists AggReported_Derived CASCADE;
CREATE VIEW AggReported_Derived as (
	SELECT year, SUM(cases) AS cases
	FROM Reported
	GROUP BY year);

drop view if exists AggReported CASCADE;
create view AggReported as (
	select year, cases, 'PRIMITIVE' as source
	from AggReported_Primitive
	union
	select year, cases, 'DERIVED' as source
	from AggReported_Derived
	);

DROP TABLE IF EXISTS Benznidazol CASCADE;
create table Benznidazol (
	year text,
	bottles FLOAT,
	primary key (year)
	);

DROP TABLE IF EXISTS Nifurtimox CASCADE;
create table Nifurtimox(
	year text, 
	tablets FLOAT,
	primary key (year)
	);

DROP TABLE IF EXISTS Treated_Primitive CASCADE;
create table Treated_Primitive (
	year TEXT, 
	cases FLOAT,
	primary key (year)
	);

drop view if exists Treated_Derived CASCADE;
CREATE VIEW Treated_Derived as (
	SELECT be.year,
       be.bottles/2 + ni.tablets/300 AS cases
	FROM Benznidazol be, Nifurtimox ni
	WHERE be.year = ni.year);

drop view if exists Treated CASCADE;
create view Treated as (
	select year, cases, 'PRIMITIVE' as source
	from Treated_Primitive
	union
	select year, cases, 'DERIVED' as source
	from Treated_Derived
	);

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
insert into AggReported_Primitive (year, cases) values ('2020',130);
insert into Benznidazol (year, bottles) values ('2020',200);
insert into Nifurtimox (year, tablets) values ('2020',15000);

select * from AggReported;
select * from Treated;

select 'DISCORDANT' as reportedDiscordancy
from AggReported
group by year
having count(distinct cases)>1;

select 'DISCORDANT' as treatedDiscordancy
from Treated 
group by year
having count(distinct cases)>1;

/*
 CREATE ASSERTION concordant CHECK (NOT EXISTS
(SELECT *
FROM AggReported re, Treated tr
WHERE re.year=tr.year AND re.cases<>tr.cases));
*/

select 'DISCORDANT' as treatedDiscordancy
FROM AggReported re, Treated tr
WHERE re.year=tr.year AND re.cases<>tr.cases;
