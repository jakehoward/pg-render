with hard_to_inline as (
 select
  %I:col,
  other_column,
  a,
  coalesce(other_other_column, 99) * 100 as "numProblems"
 from
  %I:schema.%I:tableOne
),
some_hack as (
 select
  %I:col,
  count(1) as num
 from
  %I:schema.%I:tableOne
 group by 1 order by 2 desc
),
some_hack_two as (
 select
  %I:col,
  count(1) as num
 from
  %I:schema.%I:tableOne
 group by 1 order by 2 desc
),
some_hack_three as (
 select
  %I:col,
  count(1) as num
 from
  %I:schema.%I:tableOne
 group by 1 order by 2 desc
)
select
 *
from hard_to_inline hti
left join %I:schema.%I:tableTwo tt
  on hit.dynamic_col = tt."I"
left join %I:schema.%I:tableTwo tt
  on hit.dynamic_col = tt."I"
left join %I:schema.%I:tableThree tt
  on hit.dynamic_col = tt."I"
left join %I:schema.%I:tableFour tt
  on hit.dynamic_col = tt."I"
where
 hti.a = ANY(%A:xs)
 and hti."numProblems" = %L:filter
 and foo in (%s:hackyThing)
 and blah like '%L :oths%'
limit 1000000
