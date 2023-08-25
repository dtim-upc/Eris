create or replace function pairwise_sum(a double precision[], b double precision[]) returns
double precision[] as $$
  declare 
    empty bool;
    i integer; lastpos integer; 
    result double precision[];
  begin 
    -- Find the longest array
    if array_length(a,1)>array_length(b,1) 
      then lastpos=array_length(a,1);
      else lastpos=array_length(b,1);
    end if;
    empty = true;
    -- Find the last occupied position
    while (empty and lastpos>1) 
      loop
      if (coalesce(a[lastpos],0)+coalesce(b[lastpos],0) <> 0) then empty=false; 
      else lastpos=lastpos-1; 
      end if;
      end loop;
    -- Sum the arrays until the last occupied position
    FOR i IN 1..lastpos
      LOOP
      result[i]=coalesce(a[i],0)+coalesce(b[i],0);
      END LOOP;
    return result;
  end;
$$ language plpgsql strict;

create or replace function scalar_product(a double precision[], b double precision) returns
double precision[] as $$
  declare 
    result double precision[];
  begin 
    if (b=0) then return '{0}'; 
    end if;
    FOR i IN 1..array_length(a,1)
      LOOP
      result[i]=a[i]*b;
      END LOOP;
    return result;
  end;
$$ language plpgsql;

drop aggregate if exists sum(double precision[]);
CREATE AGGREGATE sum (double precision[])
(
  sfunc = pairwise_sum,
  stype = double precision[],
  initcond = '{0}'
);

create or replace function shuffle(a double precision[], newindex integer[]) returns
double precision[] as $$
  declare
    empty bool;
    i integer; lastpos integer; 
    result double precision[];
  begin 
    empty = true; lastpos=array_length(newindex,1);
    -- Find the last occupied position
    while (empty and lastpos>1) 
      loop
      if (newindex[lastpos] is not null) then 
        if (a[newindex[lastpos]] <> 0) then empty=false; 
        else lastpos=lastpos-1; 
        end if;
      else lastpos=lastpos-1;
      end if;
      end loop;
    -- Shuffle until the last occupied position and leave the rest empty
    FOR i IN 1..lastpos
      LOOP
      if (newindex[i] is null) then result[i]=0;
      else result[i]=coalesce(a[newindex[i]],0);
      end if;
      END LOOP;
    return result;
  end;
$$ language plpgsql;
