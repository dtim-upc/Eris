create extension fuzzystrmatch;

-- Limitations we overcome:
-- 1) Query is really complex
-- 2) (in general) You cannot have more that two values per entity
-- 3) Changing the imputation of errors is not posible (e.g., changing from absolute to relative value)
-- 4) Changing the cost function is not posible (e.g., changing from sum of errors to sum of squared errors)
-- 5) All sources have the same relevance (as it is now, we cannot change this either)

create or replace view eurostats_percountry_perweek_all as (
select *
from eurostats_percountry_perweek epp 
union
select *
from eurostats_percountry_perweek_estimated eppe
);

create or replace view eurostats_percountry_perweekofyear_expected as (
select country, weekofyear, avg(deaths) as expected_deaths
from eurostats_percountry_perweek_all eppa 
  join weeks on week=id
where yearofweek between '2015' and '2019'
group by country, weekofyear
);

create or replace view eurostats_percountry_perweek_surplus as (
select eppa.country, week, eppe.expected_deaths, eppa.deaths-eppe.expected_deaths as surplus
from eurostats_percountry_perweek_all eppa 
  join weeks w on week=id 
  join eurostats_percountry_perweekofyear_expected eppe on eppa.country=eppe.country and w.weekofyear=eppe.weekofyear  
where week between '2020W06' and '2021W06' and week<>'2020W53'
)

create or replace view jhu_percountry_perweek as (
select country, week, sum(deaths) as deaths
from jhu_percountry cp 
  join dates on date=id
group by country, week
)

--------------------------------- Simply count coincidences
select country
  , count(*)
  , count(*) filter (where deaths=surplus)
  , count(*) filter (where 0.8>levenshtein(deaths::text, surplus::text)::float/greatest(length(deaths::text), length(surplus::text)))
from eurostats_percountry_perweek_surplus epps
  natural join jhu_percountry_perweek jpp 
where country in ('UK', 'ES', 'NL', 'DE', 'IT', 'SE')
group by country
order by country;

-------------------------------- Temporal evolution of differences
select country, week
  , power((deaths-surplus)/expected_deaths,2)
from eurostats_percountry_perweek_surplus epps
  natural join jhu_percountry_perweek jpp 
where country in ('UK', 'ES', 'NL', 'DE', 'IT', 'SE')
order by country, week;


