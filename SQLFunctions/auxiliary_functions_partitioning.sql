drop aggregate if exists keep_any(double precision);
drop function if exists keep_any(a double precision, b double precision);

create or replace function keep_any(a double precision, b double precision) returns
double precision as $$
  begin
    if (a<>0) THEN return a;
    else return b;
    end if;
  end;
$$ language plpgsql strict;

CREATE AGGREGATE keep_any (double precision)
(
  sfunc = keep_any,
  stype = double precision,
  initcond = 0
);
