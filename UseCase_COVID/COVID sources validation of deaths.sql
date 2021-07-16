select '5_jhu_perCountry_perDay' as tablename, COUNT(distinct country) as places, COUNT(distinct date) as timepoints, count(*) as rows, min(case when cases is null then null else date end) as fisttimepoint, max(case when cases is null then null else date end) as lasttimepoint
from jhu_percountry jp
UNION
select '6_COVIDData_perRegion_perDay' as tablename, COUNT(distinct region) as places, COUNT(distinct date) as timepoints, count(*) as rows, min(case when cases is null then null else date end) as fisttimepoint, max(case when cases is null then null else date end) as lasttimepoint
from coviddata_perregion cp 
UNION
select '3_EUROStats_perCountry_perWeek' as tablename, COUNT(distinct country) as places, COUNT(distinct week) as timepoints, count(*) as rows, min(case when deaths is null then null else week end) as fisttimepoint, max(case when deaths is null then null else week end) as lasttimepoint
from eurostats_percountry_perweek epp  
UNION
select '4_EUROStats_perCountry_perWeek_estimated' as tablename, COUNT(distinct country) as places, COUNT(distinct week) as timepoints, count(*) as rows, min(case when deaths is null then null else week end) as fisttimepoint, max(case when deaths is null then null else week end) as lasttimepoint
from eurostats_percountry_perweek_estimated epp  
UNION
select 'EUROStats_perCountry_perYear' as tablename, COUNT(distinct country) as places, COUNT(distinct year) as timepoints, count(*) as rows, min(case when deaths is null then null else year end) as fisttimepoint, max(case when deaths is null then null else year end) as lasttimepoint
from eurostats_percountry_peryear epp2 
UNION
select '1_EUROStats_perRegion_perWeek' as tablename, COUNT(distinct region) as places, COUNT(distinct week) as timepoints, count(*) as rows, min(case when deaths is null then null else week end) as fisttimepoint, max(case when deaths is null then null else week end) as lasttimepoint
from eurostats_perregion ep 
UNION
select '2_EUROStats_perRegion_perWeek_estimated' as tablename, COUNT(distinct region) as places, COUNT(distinct week) as timepoints, count(*) as rows, min(case when deaths is null then null else week end) as fisttimepoint, max(case when deaths is null then null else week end) as lasttimepoint
from eurostats_perregion_estimated ep 
order by tablename
;

-- All estatistics are either confirmed or estimated
select * 
from eurostats_percountry_perweek epp 
  join eurostats_percountry_perweek_estimated eppe 
  on epp.country =eppe.country and epp.week =eppe.week
union 
select * 
from eurostats_perregion ep 
  join eurostats_perregion_estimated epe 
  on ep.region =epe.region and ep.week =epe.week;

-- Some countries have a W99 for some years. These are registered in eurostats_percountry_peryear
select epp.country, w.year, sum(epp.deaths), keep_any(epp2.deaths)
from eurostats_percountry_perweek epp 
  join weeks w on epp.week=w.id 
  join eurostats_percountry_peryear epp2 on epp.country=epp2.country and epp2.year=w.year
group by epp.country, w.year
having sum(epp.deaths)<>keep_any(epp2.deaths)
order by 1,2 desc;

-- Some regions do not add up to the country per week
select count(*), count(distinct c.country)
from eurostats_percountry_perweek c
  join (
    select country, week, sum(deaths) as deaths
    from eurostats_perregion ep
      join firstadminlevels f2 
        on ep.region=f2.id
    group by country, week) r 
    on c.country=r.country and c.week=r.week
where c.deaths<>r.deaths and left(c.week,4)='2020';

-- Some countries with quite estable mortality per month in the last years have a huge difference in 2020
select epp.country, w.weekofyear, keep_any(current.deaths), avg(epp.deaths), stddev(epp.deaths), stddev(epp.deaths)/avg(epp.deaths) as coefficientOfVariation, keep_any(current.deaths)/avg(epp.deaths) as difference
from eurostats_percountry_perweek epp 
  join weeks w on epp.week=w.id
  join (
    select country, weekofyear, deaths
    from eurostats_percountry_perweek epp 
      join weeks w on epp.week=w.id 
    where w.year='2020' and w.weekofyear>'10') current on current.country=epp.country and current.weekofyear=w.weekofyear 
where w.year>='2015' and w.year<'2020' 
group by epp.country, w.weekofyear 
having stddev(epp.deaths)/avg(epp.deaths)<0.05
order by difference desc;
