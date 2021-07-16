
-------------------------------------- Surplus deaths according to EUROStats per region per week
(((
  (
    (
      (
        (
          (
            firstadminlevels(country='ES')
          ){id->region}
        )[region SUM]
      )
      JOIN
      ( (eurostats_perregion UNION eurostats_perregion_estimated){deaths->current})
    )
    JOIN
    (
      (
      weeks{id->week}
      )(year='2020' AND weekofyear>'03' and weekofyear<'46')
    )
  )
  JOIN
  (
    (
      (
        (
          (
            (
              (
                firstadminlevels(country='ES')
              ){id->region}
            )[region SUM]
          )
          JOIN
          ((eurostats_perregion UNION eurostats_perregion_estimated){counter:=1})
        )
        JOIN
        (
          (
          weeks{id->week}
          )(year>'2014' AND year<'2020')
        )
      )[weekofyear, region SUM deaths, counter]
    ){average:=deaths/counter}
  )
){negavg:=-1*average}){surplus:=current+negavg})[surplus]

SELECT COUNT(*)
FROM eurostats_perregion;
SELECT COUNT(*)
FROM eurostats_perregion_estimated;
select COUNT(*)
from eurostats_perregion ep 
  join eurostats_perregion_estimated epe 
  on ep.region = epe.region and ep.week = epe.week; 
select count(*)
from (select * from eurostats_perregion union all select * from eurostats_perregion_estimated) u
  join weeks w on u.week=w.id
  join firstadminlevels r on u.region=r.id
where r.country='FR'
  and year>'2014' and year<'2020';
select count(*)
from (select * from eurostats_perregion union all select * from eurostats_perregion_estimated) u
  join weeks w on u.week=w.id
  join firstadminlevels r on u.region=r.id
where r.country='FR'
  and year='2020' AND weekofyear>'03';
select weekofyear, region, sum(deaths) as sum, count(*) as count, sum(deaths)/count(*) as average
from (select * from eurostats_perregion union all select * from eurostats_perregion_estimated) u
  join weeks w on u.week=w.id
  join firstadminlevels r on u.region=r.id
where r.country='FR'
  and year>'2014' and year<'2020'
group by weekofyear, region;
select count(distinct weekofyear) as weeks, count(distinct region) as regions, count(*) as rows
from (
select weekofyear, region, deaths, refval.average, deaths-refval.average
from (select * from eurostats_perregion union all select * from eurostats_perregion_estimated) u
  join weeks w on u.week=w.id
  join firstadminlevels r on u.region=r.id
  natural join (
    select weekofyear, region, sum(deaths) as sum, count(*) as count, sum(deaths)/count(*) as average
    from (select * from eurostats_perregion ep union select * from eurostats_perregion_estimated epe) u
      join weeks w on u.week=w.id
      join firstadminlevels r on u.region=r.id
    where r.country='FR'
      and year>'2014' and year<'2020'
    group by weekofyear, region) refval
where r.country='FR'
  and year='2020' AND weekofyear>'03') q;

-------------------------------------- Surplus deaths according to EUROStats per country per week
(((
  (
    (
      ((eurostats_percountry_perweek UNION eurostats_percountry_perweek_estimated){deaths->current})(country='ES')
    )
    JOIN
    (
      (
      weeks{id->week}
      )(year='2020' AND weekofyear>'03' and weekofyear<'46')
    )
  )
  JOIN
  (
    (
      (
        (
          ((eurostats_percountry_perweek  UNION eurostats_percountry_perweek_estimated){counter:=1})(country='ES')
        )
        JOIN
        (
          (
          weeks{id->week}
          )(year>'2014' AND year<'2020')
        )
      )[weekofyear, country SUM deaths, counter]
    ){average:=deaths/counter}
  )
){negavg:=-1*average}){surplus:=current+negavg})[surplus]

SELECT COUNT(*)
FROM eurostats_percountry_perweek;
SELECT COUNT(*)
FROM eurostats_percountry_perweek_estimated;
select COUNT(*)
from eurostats_percountry_perweek ep 
  join eurostats_percountry_perweek_estimated epe 
  on ep.country = epe.country and ep.week = epe.week; 
select count(*)
from (select * from eurostats_percountry_perweek union all select * from eurostats_percountry_perweek_estimated) u
  join weeks w on u.week=w.id
where u.country='ES'
  and year>'2014' and year<'2020';
select count(*)
from (select * from eurostats_percountry_perweek union all select * from eurostats_percountry_perweek_estimated) u
  join weeks w on u.week=w.id
where u.country='ES'
  and year='2020' AND weekofyear>'03';
select weekofyear, country, sum(deaths) as sum, count(*) as count, sum(deaths)/count(*) as average
from (select * from eurostats_percountry_perweek union all select * from eurostats_percountry_perweek_estimated) u
  join weeks w on u.week=w.id
where country='ES'
  and year>'2014' and year<'2020'
group by weekofyear, country;
select count(distinct weekofyear) as weeks, count(distinct country) as countries, count(*) as rows
from (
select weekofyear, country, deaths, refval.average, deaths-refval.average
from (select * from eurostats_percountry_perweek union all select * from eurostats_percountry_perweek_estimated) u
  join weeks w on u.week=w.id
  natural join (
    select weekofyear, country, sum(deaths) as sum, count(*) as count, sum(deaths)/count(*) as average
    from (select * from eurostats_percountry_perweek union all select * from eurostats_percountry_perweek_estimated) u
      join weeks w on u.week=w.id
    where country='FR'
      and year>'2014' and year<'2020'
    group by weekofyear, country) refval
where country='FR'
  and year='2020' AND weekofyear>'03') q;

-------------------------------------- Surplus deaths according to EUROStats per country per year (Not necessary because of being weaker than the previous, since we cannot consider the table per country per year, because it does not make sense for 2020 and partial weeks).
(((
  (
    (
        (
          (
            (
              (eurostats_percountry_perweek  UNION eurostats_percountry_perweek_estimated)
            )
            JOIN
            (
              (
              weeks{id->week}
              )(year='2020' AND weekofyear>'03' and weekofyear<'46')
            )
          )[country SUM deaths]
        )
    ){deaths->current}
  )
  JOIN
  (
    (
      (
        (
            (
              (
                (eurostats_percountry_perweek UNION eurostats_percountry_perweek_estimated)
              )
              JOIN
              (
                (
                weeks{id->week}
                )(year>'2014' AND year<'2020' AND weekofyear>'03' and weekofyear<'46')
              )
            )[country, year SUM deaths]
        ){counter:=1}
      )[country SUM deaths, counter]
    ){average:=deaths/counter}
  )
){negavg:=-1*average}){surplus:=current+negavg})[surplus]

----------------------------------------------------------------------- COVID Deaths according to JHU
(
  (
    (jhu_percountry(country='ES'))
    JOIN
    ((dates{id->date}) JOIN (weeks{id->week}))
  )[country, weekofyear SUM deaths]
){deaths->surplus}

select count(*)
from jhu_percountry
where country='ES';
select country, weekofyear, sum(deaths) as COVIDDeaths
from jhu_percountry j
  join dates d on j.date=d.id 
  join weeks w on d.week=w.id
where country='ES'
group by country, weekofyear;

----------------------------------------------------------------------- Coalesce demographics per country and per region
(
  (
    ( 
      (
        (
          (
            ((( ( ( ( ( ( firstadminlevels(country='ES')){id->region})[region SUM]) JOIN ( (eurostats_perregion UNION eurostats_perregion_estimated){deaths->current})) JOIN ( ( weeks{id->week})(year='2020' AND weekofyear>'03' and weekofyear<'46'))) JOIN ( ( ( ( ( ( ( firstadminlevels(country='ES')){id->region})[region SUM]) JOIN ((eurostats_perregion UNION eurostats_perregion_estimated){counter:=1})) JOIN ( ( weeks{id->week})(year>'2014' AND year<'2020')))[weekofyear, region SUM deaths, counter]){average:=deaths/counter})){negavg:=-1*average}){surplus:=current+negavg})[surplus]
          )
          JOIN
          (firstadminlevels{id->region})
        )[country, weekofyear SUM surplus]
      )
      DUNION[source1]
      (
        (
          ((( ( ( ((eurostats_percountry_perweek UNION eurostats_percountry_perweek_estimated){deaths->current})(country='ES')) JOIN ( ( weeks{id->week})(year='2020' AND weekofyear>'03' and weekofyear<'46'))) JOIN ( ( ( ( ((eurostats_percountry_perweek  UNION eurostats_percountry_perweek_estimated){counter:=1})(country='ES')) JOIN ( ( weeks{id->week})(year>'2014' AND year<'2020')))[weekofyear, country SUM deaths, counter]){average:=deaths/counter})){negavg:=-1*average}){surplus:=current+negavg})[surplus] 
        )[country, weekofyear SUM surplus]
      )
    )[COAL source1]
  )
  DUNION[source2]
  (
    ( ( (jhu_percountry(country='ES')) JOIN ((dates{id->date}) JOIN (weeks{id->week})))[country, weekofyear SUM deaths]){deaths->surplus}
  )
)[COAL source2]

select asperjhu.country, asperjhu.weekofyear, asperjhu.date, surplusasperregion, surplusaspercountry, coviddeaths 
from (
select weekofyear, country, sum(surplus) as surplusasperregion
from (
  select weekofyear, region, r.country, deaths, refval.average, deaths-refval.average as surplus 
  from (select * from eurostats_perregion union all select * from eurostats_perregion_estimated) u
  join weeks w on u.week=w.id
  join firstadminlevels r on u.region=r.id
  natural join (
      select weekofyear, region, sum(deaths) as sum, count(*) as count, sum(deaths)/count(*) as average
      from (select * from eurostats_perregion ep union select * from eurostats_perregion_estimated epe) u
        join weeks w on u.week=w.id
        join firstadminlevels r on u.region=r.id
      where r.country='ES'
        and year>'2014' and year<'2020'
      group by weekofyear, region) refval
  where r.country='ES'
    and year='2020' AND weekofyear>'03') q
group by weekofyear, country) asperregion
natural join (
select weekofyear, country, deaths-refval.average as surplusaspercountry
from (select * from eurostats_percountry_perweek union all select * from eurostats_percountry_perweek_estimated) u
  join weeks w on u.week=w.id
  natural join (
    select weekofyear, country, sum(deaths) as sum, count(*) as count, sum(deaths)/count(*) as average
    from (select * from eurostats_percountry_perweek union all select * from eurostats_percountry_perweek_estimated) u
      join weeks w on u.week=w.id
    where country='ES'
      and year>'2014' and year<'2020'
    group by weekofyear, country) refval
where country='ES'
  and year='2020' AND weekofyear>'03') aspercountry
natural join (
select country, date, weekofyear, sum(deaths) as COVIDDeaths
from jhu_percountry j
  join dates d on j.date=d.id 
  join weeks w on d.week=w.id
where country='ES'
group by country, date, weekofyear
) asperjhu
order by date;

---------------------------------------------------------- Regional JHU deaths per country per day
(
  ((firstadminlevels(country='ES')){id->region})
  JOIN
  (coviddata_perregion(date>'20200112' and date<'20201109'))
)[country, date SUM deaths]

---------------------------------------------------------- Regional JHU deaths per region per week
(
  (
    ((firstadminlevels(country='ES')){id->region})
    JOIN
    (coviddata_perregion(date>'20200112' and date<'20201109'))
  )
  JOIN 
  ((dates{id->date}) join (weeks{id->week}))
)[country, region, weekofyear SUM deaths]

---------------------------------------------------------- Regional JHU cases per week ported to the future (WARNING: THIS WILL GENERATE AN EXTRA EQUATION)
(
  (
    ((firstadminlevels(country='ES')){id->region})
    JOIN
    (coviddata_perregion(date>'20200105' and date<'20201102'))
  )
  JOIN 
  ((dates{id->date}) join (weeks{weekminus1->week}))
)[country, region, weekofyear SUM cases]

---------------------------------------------------------- Coalesced regional JHU deaths and cases per week ported to the future
(
  (
    (
      ( ( ((firstadminlevels(country='ES')){id->region}) JOIN (coviddata_perregion(date>'20200112' and date<'20201109'))) JOIN ((dates{id->date}) join (weeks{id->week})))[country, region, weekofyear SUM deaths]
    ){deaths->surplus}  
  )
  DUNION[source]
  (
    ( (
      ( ( ((firstadminlevels(country='ES')){id->region}) JOIN (coviddata_perregion(date>'20200105' and date<'20201102'))) JOIN ((dates{id->date}) join (weeks{weekminus1->week})))[country, region, weekofyear SUM cases]
    ){surplus:=0.015*cases})[surplus]
  )
)[COAL source]

----------------------------------------------------------- Coalesced JHU deaths
  (
    ( 
      (
        (
          (
            (
              (
                (
                  ( (jhu_percountry(country='ES'))[deaths])
                )
                DUNION[source]
                (
                  ( ((firstadminlevels(country='ES')){id->region}) JOIN (coviddata_perregion(date>'20200112' and date<'20201109')))[country, date SUM deaths]
                )
              )[COAL source]
            )
            JOIN
            ((dates{id->date}) join (weeks{id->week}))
          )[country, weekofyear SUM deaths]
        ){deaths->surplus}
      )
      DUNION[source1]
      (
        (
          (
            (
              (
                ( ( ((firstadminlevels(country='ES')){id->region}) JOIN (coviddata_perregion(date>'20200112' and date<'20201109'))) JOIN ((dates{id->date}) join (weeks{id->week})))[country, region, weekofyear SUM deaths]
              ){deaths->surplus}  
            )
          DUNION[source]
            (
              (
                ( ( ((firstadminlevels(country='ES')){id->region}) JOIN (coviddata_perregion(date>'20200105' and date<'20201102'))) JOIN ((dates{id->date}) join (weeks{weekminus1->week})))[country, region, weekofyear SUM cases]
              ){cases->surplus}
            )
          )[COAL source]
        )[country, weekofyear SUM surplus]
      )
    )[COAL source1]
  )
  
  ----------------------------------------------- Coalesced JHU and EUROStats
(
  (
    ( 
      (
        (
          (
            ((( ( ( ( ( ( firstadminlevels(country='ES')){id->region})[region SUM]) JOIN ( (eurostats_perregion UNION eurostats_perregion_estimated){deaths->current})) JOIN ( ( weeks{id->week})(year='2020' AND weekofyear>'03' and weekofyear<'46'))) JOIN ( ( ( ( ( ( ( firstadminlevels(country='ES')){id->region})[region SUM]) JOIN ((eurostats_perregion UNION eurostats_perregion_estimated){counter:=1})) JOIN ( ( weeks{id->week})(year>'2014' AND year<'2020')))[weekofyear, region SUM deaths, counter]){average:=deaths/counter})){negavg:=-1*average}){surplus:=current+negavg})[surplus]
          )
          JOIN
          (firstadminlevels{id->region})
        )[country, weekofyear SUM surplus]
      )
      DUNION[source1]
      (
        (
          ((( ( ( ((eurostats_percountry_perweek UNION eurostats_percountry_perweek_estimated){deaths->current})(country='ES')) JOIN ( ( weeks{id->week})(year='2020' AND weekofyear>'03' and weekofyear<'46'))) JOIN ( ( ( ( ((eurostats_percountry_perweek  UNION eurostats_percountry_perweek_estimated){counter:=1})(country='ES')) JOIN ( ( weeks{id->week})(year>'2014' AND year<'2020')))[weekofyear, country SUM deaths, counter]){average:=deaths/counter})){negavg:=-1*average}){surplus:=current+negavg})[surplus] 
        )[country, weekofyear SUM surplus]
      )
    )
  )
  DUNION[source2]
  (
      ( ( ( ( ( ( ( ( (jhu_percountry(country='ES'))[deaths])) DUNION[source] ( ( ((firstadminlevels(country='ES')){id->region}) JOIN (coviddata_perregion(date>'20200112' and date<'20201109')))[country, date SUM deaths]))[COAL source]) JOIN ((dates{id->date}) join (weeks{id->week})))[country, weekofyear SUM deaths]){deaths->surplus}) DUNION[source1] ( ( ( ( ( ( ( ((firstadminlevels(country='ES')){id->region}) JOIN (coviddata_perregion(date>'20200112' and date<'20201109'))) JOIN ((dates{id->date}) join (weeks{id->week})))[country, region, weekofyear SUM deaths]){deaths->surplus}  ) DUNION[source] ( ( ( ( ((firstadminlevels(country='ES')){id->region}) JOIN (coviddata_perregion(date>'20200105' and date<'20201102'))) JOIN ((dates{id->date}) join (weeks{weekminus1->week})))[country, region, weekofyear SUM cases]){cases->surplus}))[COAL source])[country, weekofyear SUM surplus]))
  )
)[COAL source1,source2]
