select 'COVIDData_perCountry' as tablename, COUNT(distinct country) as countries, COUNT(distinct date) as dates, count(*) as rows
from coviddata_percountry cp
UNION
select 'JHU_perCountry' as tablename, COUNT(distinct country) as countries, COUNT(distinct date) as dates, count(*) as rows
from jhu_percountry hp
UNION
select 'Differences' as tablename,  COUNT(distinct cp.country) as countries, COUNT(distinct cp.date) as dates, count(*) as rows
from coviddata_percountry cp join jhu_percountry hp on cp.country=hp.country and cp.date=hp.date
where cp.cases<>hp.cases or cp.deaths<>hp.deaths;-- or ep.country is null or op.country is null
;

select 'COVIDData_perRegion' as tablename, COUNT(distinct region) as regions, COUNT(distinct date) as dates, count(*) as rows
from coviddata_perregion cp
UNION
select 'JHU_perRegion' as tablename, COUNT(distinct region) as regions, COUNT(distinct date) as dates, count(*) as rows
from jhu_perregion hp
UNION
select 'Differences' as tablename, COUNT(distinct cp.region) as regions, COUNT(distinct cp.date) as dates, count(*) as rows
from coviddata_perregion cp join jhu_perregion hp on cp.region=hp.region and cp.date =hp.date
where cp.cases<>hp.cases or cp.deaths<>hp.deaths
;

----------- Few data at country and regional level at Git coincides
select count(*)
from coviddata_percountry ep join (
  select f.country, cp.date, sum(cases) as cases, sum(deaths) as deaths
  from coviddata_perregion cp join firstadminlevels f on cp.region=f.id
  group by f.country, cp.date) cp 
  on ep.country=cp.country and ep.date=cp.date
where ep.cases=cp.cases and ep.deaths=cp.deaths;

----------- The problem is not only completeness, because in many cases the regions add up more
select count(*)
from coviddata_percountry ep join (
  select f.country, cp.date, sum(cases) as cases, sum(deaths) as deaths
  from coviddata_perregion cp join firstadminlevels f on cp.region=f.id
  group by f.country, cp.date) cp 
  on ep.country=cp.country and ep.date=cp.date
where ep.cases<cp.cases or ep.deaths<cp.deaths;

----------- Many countries have some negative declaration of cases or deaths in one date according to JHU
select count(distinct country)
from (
  select cp.country, cp.date 
  from jhu_percountry cp --join dates d on cp.date=d.id
  group by cp.country, cp.date 
  having SUM(cases)<0 or sum(deaths)<0
  ) _;

----------- Few countries have some negative declaration of cases or deaths in one week to JHU
select count(distinct country)
from (
  select cp.country, d.week
  from jhu_percountry cp join dates d on cp.date=d.id
  group by cp.country, d.week 
  having SUM(cases)<0 or sum(deaths)<0
  ) _;

----------- Only five countries have some negative declaration of cases or deaths in one month to JHU
select count(distinct country)
from (
  select cp.country, d.month
  from jhu_percountry cp join dates d on cp.date=d.id
  group by cp.country, d.month 
  having SUM(cases)<0 or sum(deaths)<0
  ) _;
 
 ----------- Most regions have some negative declaration of cases or deaths in one date
select count(distinct region)
from (
  select cp.region, d.id 
  from coviddata_perregion cp join dates d on cp.date=d.id
  group by cp.region, d.id 
  having SUM(cases)<0 or sum(deaths)<0
  ) _;

 ----------- Some regions have some negative declaration of cases or deaths in one week
select count(distinct region)
from (
  select cp.region, d.week 
  from coviddata_perregion cp join dates d on cp.date=d.id
  group by cp.region, d.week 
  having SUM(cases)<0 or sum(deaths)<0
  ) _;

 ----------- Few regions still have some negative declaration of cases or deaths in one month
select count(distinct region)
from (
  select cp.region, d.month
  from coviddata_perregion cp join dates d on cp.date=d.id
  group by cp.region, d.month 
  having SUM(cases)<0 or sum(deaths)<0
  ) _
 having count(*)>1;

