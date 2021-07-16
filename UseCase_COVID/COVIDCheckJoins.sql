--------------- Eurostats per region (should be ninety-five plus nineteen)
select weekofyear, count(*)
from eurostats_perregion epp 
  join weeks w on w.id = epp.week 
  join firstadminlevels f2 on f2.id=epp.region
where country='ES' and year>'2014'
group by weekofyear
order by weekofyear;

--------------- Eurostats per country (should be five plus one)
select weekofyear, count(*)
from eurostats_percountry_perweek epp
  join weeks w on w.id = epp.week 
where country='ES' and year>'2014'
group by weekofyear
order by weekofyear;

--------------- JHU per country and week (should be seven)
select week , count(*)
from jhu_percountry jp 
  join dates d on jp.date=d.id 
where country='ES'
group by week
order by week;

--------------- CovidData per region and week (should be 133)
select week, count(*)
from coviddata_perregion cp
  join firstadminlevels f2 on f2.id=cp.region
  join dates d on cp.date=d.id 
where country='ES'
group by week
order by week;

------------------------- Get all weeks between 07 and 45

select w.weekofyear, min(dpast.id) as paststart, max(dpast.id) as pastend, min(dcurrent.id) as curentstart, max(dcurrent.id) as currentend 
from weeks w
  join weeks past on w.weekminus2 = past.id 
  join dates dcurrent on w.id=dcurrent.week
  join dates dpast on past.id=dpast.week 
where w.year='2020' and w.weekofyear>='08' and w.weekofyear<='45'
group by w.weekofyear 
order by w.weekofyear;

-------------------------- Countries reporting regions at both EUROStats and JHU
select distinct ep.country
from coviddata_perregion cp 
  join eurostats_percountry_perweek ep on left(cp.region,2)=ep.country
order by 1;
