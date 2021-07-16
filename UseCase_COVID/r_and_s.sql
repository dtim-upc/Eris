
DROP TABLE IF EXISTS r;
DELETE FROM schema WHERE tablename='r';
CREATE TABLE r (
	a text NOT NULL,
	b text NOT NULL,
	c float8 NULL,
	d float8 NULL,
	e float8 NULL,
	f float8 NULL
);

DROP TABLE IF EXISTS s;
DELETE FROM schema WHERE tablename='s';
CREATE TABLE s (
	a text NOT NULL,
	b text NOT NULL,
	c float8 NULL,
	d float8 NULL
);

insert into schema values ('s','a',true,false); 
insert into schema values ('s','b',true,false); 
insert into schema values ('s','c',false,false); 
insert into schema values ('s','d',false,false); 
insert into schema values ('r','a',true,false); 
insert into schema values ('r','b',true,false); 
insert into schema values ('r','c',false,false); 
insert into schema values ('r','d',false,false); 
insert into schema values ('r','e',false,true); 
insert into schema values ('r','f',false,true); 

insert into r values ('1','2',3,4,1,2);
insert into r values ('5','6',7,8,3,4);
insert into r values ('1','6',9,10,5,6);
insert into r values ('5','2',11,12,7,8);
insert into s values ('1','2',3,4);
insert into s values ('5','6',7,8);
insert into s values ('1','6',9,10);
insert into s values ('5','2',11,12);

/* Algebraic queries
r
r[c]
r[d]
r[e]
r[f]
r[c,e]
r(a='1')
r{c->g}
r{f->g}
r{f->g,d->h}
r[^e,f] UNION s
r DUNION[discr] r
r[a SUM c]
r[a SUM f]
r[a SUM c,f]
r[SUM f]
r[SUM c]
r[SUM c,d]
(r[SUM c,d]){s:=c+d}
(r[c,d]) JOIN (r[e,f])
r JOIN (s{c->g,d->h})
(r{new:=5})[new]
(r{new:=c})[c,new]
(r{new:=f})[f,new]
((r{new:=5}){p:=-5*new})[new,p]
((r{new:=5}){p:=5*new})[new,p]
((r{new:=5}){p:=new*5})[new,p]
((r{new:=5}){p:=new*f})[f,new,p]
((r{new:=5}){s:=new+5})[new,s]
((r{new:=5}){s:=5+new})[new,s]
((r{new:=5}){s:=new+f})[f,new,s]
((r{new:=5}){s:=5/new})[new,s]
((r{new:=5}){s:=new/5})[new,s]
((r{new:=5}){s:=new/f})[f,new,s]
(r{p:=c*5})[c,p]
(r{p:=c*f})[c,f,p]
(r{s:=c+5})[c,s]
(r{s:=5+c})[c,s]
(r{s:=c+f})[c,f,s]
(r{s:=f+c})[c,f,s]
(r{s:=c/5})[c,s]
(r{s:=c/f})[f,c,s]
(r{s:=f/c})[f,c,s]
(r{s:=5/c})[c,s]
(r{p:=c*d})[c,f,p]
(r{s:=c+d})[c,d,s]
(r{s:=c/d})[c,d,s]
r UNION r
(r[c,d]) UNION (s{g->c,h->d})
(r[e,f]) UNION (s{g->e,h->f})
(r[c,f]) UNION (s{g->c,h->f})
(r[e,d]) UNION (s{g->e,h->d})
(s{g->e,h->f}) UNION (r[e,f])
(r[c,d]) DUNION[discr] (s{g->c,h->d})
(r[e,f]) DUNION[discr] (s{g->e,h->f})
(s{g->e,h->f}) DUNION[discr] (r[e,f])
(r[e,f]) DUNION[discr] (s{g->e,h->f})
*/
