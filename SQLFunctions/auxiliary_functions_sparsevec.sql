drop aggregate if exists sum(sparsevec);
drop aggregate if exists keep_any(sparsevec);
drop function if exists scalar_product(v sparsevec, s double precision);
drop function if exists pairwise_sum(a sparsevec, b sparsevec);
drop function if exists keep_any(a sparsevec, b sparsevec);
drop type if exists sparsevec;
drop type if exists term;

CREATE TYPE term AS (
  coeff double precision,
  varid text
);

CREATE TYPE sparsevec AS (
  terms term[],
  constant double precision
);

create or replace function scalar_product(v sparsevec, s double precision) returns
sparsevec as $$
  begin 
    if (s=0) then 
      return ROW(array[]::term[],0)::sparsevec;
    else
      return row(
               array (
                 select row(s*coeff,varid)::term 
                 from unnest(v.terms)
                 )::term[],
               s*v.constant
             )::sparsevec;
    end if;
  end;
$$ language plpgsql;

create or replace function pairwise_sum(a sparsevec, b sparsevec) returns
sparsevec as $$
  begin 
  return row(
    array (
      select row(sum(coeff),varid)::term 
      from ( 
        select * from unnest(a.terms)
        union all
        select * from unnest(b.terms)
        ) _
      group by varid
      having sum(coeff)<>0
      )::term[],
    a.constant+b.constant
    )::sparsevec;
  end;
$$ language plpgsql strict;
/* This implementation is slower than the previous one
create or replace function pairwise_sum(a sparsevec, b sparsevec) returns
sparsevec as $$
  declare
  ia integer;
  ib integer;
  ir integer;
  ar term[];
  begin 
  ia=1;ib=1;ir=1;
  ar='{}';
  while (ia<=array_length(a.terms,1) and ib<=array_length(b.terms,1)) loop
    if (a.terms[ia].varid=b.terms[ib].varid) then ar[ir]=(a.terms[ia].coeff+b.terms[ib].coeff,a.terms[ia].varid); ia=ia+1; ib=ib+1;
    else 
      if (a.terms[ia].varid<b.terms[ib].varid) then ar[ir]=(a.terms[ia].coeff,a.terms[ia].varid); ia=ia+1;
      else ar[ir]=(b.terms[ib].coeff,b.terms[ib].varid); ib=ib+1;
      end if;
    end if;
    ir=ir+1;
  end loop;
  while (ia<=array_length(a.terms,1) or ib<=array_length(b.terms,1)) loop
    if (ia<=array_length(a.terms,1)) then ar[ir]=(a.terms[ia].coeff,a.terms[ia].varid); ia=ia+1;
    else ar[ir]=(b.terms[ib].coeff,b.terms[ib].varid); ib=ib+1;
    end if;
    ir=ir+1;
  end loop;
  return row(ar,a.constant+b.constant)::sparsevec;
  end;
$$ language plpgsql strict;
*/
CREATE AGGREGATE sum (sparsevec)
(
  sfunc = pairwise_sum,
  stype = sparsevec,
  initcond = '({},0)'
);

create or replace function keep_any(a sparsevec, b sparsevec) returns
sparsevec as $$
  begin
    if (a<>ROW(array[]::term[],0)::sparsevec) THEN return a;
    else return b;
    end if;
  end;
$$ language plpgsql strict;

CREATE AGGREGATE keep_any (sparsevec)
(
  sfunc = keep_any,
  stype = sparsevec,
  initcond = '({},0)'
);
