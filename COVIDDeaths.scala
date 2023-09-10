object COVIDDeaths {

  def main(args:Array[String]) : Unit = {
    Class.forName("org.postgresql.Driver")

    val hostname = args(0)
    val dbname = args(1)
    val username = args(2)
    val password = args(3)
    val encoding = Encoding.encoder_to_use(args.applyOrElse(4,{_:Int =>"partitioning"}))

    val connector = Connector(hostname, dbname, username, password)

    //./run-loader.sh $1 $2 $3 $4 r2 $5
    Loader.load(connector,"countries", encoding, false)
    Loader.load(connector,"dates", encoding, false)
    Loader.load(connector,"firstadminlevels", encoding, false)
    Loader.load(connector,"weeks", encoding, false)
//    Loader.load(connector,"coviddata_percountry", encoding, false)
    Loader.load(connector,"coviddata_perregion", encoding, false)
    Loader.load(connector,"eurostats_percountry_perweek", encoding, false)
    Loader.load(connector,"eurostats_percountry_perweek_estimated", encoding, false)
//    Loader.load(connector,"eurostats_percountry_peryear", encoding, false)
    Loader.load(connector,"eurostats_perregion", encoding, false)
    Loader.load(connector,"eurostats_perregion_estimated", encoding, false)
    Loader.load(connector,"jhu_percountry", encoding, false)
//    Loader.load(connector,"jhu_perregion", encoding, false)

    val conn = connector.getConnection()
    // Because of Brexit, Eurostats only provides data of UK until 2020-W51
//    val countries = List("DE","ES","IT","NL","SE","UK") //"RU","FR","NO", "DK",
    val countries = List("ES") //"RU","FR","NO", "DK",

    println("KindOfQuery;Country;Week;#Eq;#Vars;Eq. creation time;Solve time;Average squared error")
/*
    countries.map{co =>
         // There is no estimation of deaths, because W53 is quite exceptional and it does not exists all years (thus, this is avoided)
        val timequery = "select d.id from dates d where d.week>='2020W20' and d.week<='2021W06' and d.week<>'2020W53' order by d.id;"
//        val timequery = "select w.id from weeks w where w.id>='2020W20' and w.id<='2021W06' and w.id<>'2020W53' order by w.id;"
//        val timequery = " select w.id from weeks w where w.id='2020W05' order by w.id;"
       
      val preparedStatement = conn.prepareStatement(timequery)
      val res = List() ++ Database.streamQuery(preparedStatement)
      res.map{row =>
//        val wid = row("id").get
//        val (_,objective,eqs,vars,eqCreationTime,solveTime) = VirtualSolver.solve(connector, "((((jhu_percountry(country='"+co+"')) JOIN ((dates(week='"+wid+"')){id->date}))[country, week SUM deaths]) DUNION[source] (((coviddata_perregion JOIN ((firstadminlevels{id->region})(country='"+co+"'))) JOIN ((dates(week='"+wid+"')){id->date}))[country, week SUM deaths]))[COAL source]", encoding)
//        val (_,objective,eqs,vars,eqCreationTime,solveTime) = VirtualSolver.solve(connector, "((((jhu_percountry(country='"+co+"')) JOIN ((dates(week='"+wid+"')){id->date}))[country, week SUM cases]) DUNION[source] (((coviddata_perregion JOIN ((firstadminlevels{id->region})(country='"+co+"'))) JOIN ((dates(week='"+wid+"')){id->date}))[country, week SUM cases]))[COAL source]", encoding)
        val da = row("id").get
        val (_,objective,eqs,vars,eqCreationTime,solveTime) = VirtualSolver.solve(connector, "( ((jhu_percountry(country='"+co+"' and date='"+da+"'))[cases]) DUNION[source] ( ( (coviddata_perregion(date='"+da+"')) JOIN ((firstadminlevels{id->region})(country='"+co+"')))[country, date SUM cases]))[COAL source]", encoding)
//        println(s"WithJHURegions;$co;$wid;$eqs;$vars;"+eqCreationTime+";"+solveTime+";"+objective)
        println(s"WithJHURegions;$co;$da;$eqs;$vars;"+eqCreationTime+";"+solveTime+";"+objective)
      }
    }
  */

//    for (shift <- 1 to 8) {
    for (shift <- 1 to 1) {
      countries.map{co =>
         // There is no estimation of deaths, because W53 is quite exceptional and it does not exists all years (thus, this is avoided)
        val timequery = " select w.id, min(dpast.id) as paststart, max(dpast.id) as pastend, min(dcurrent.id) as currentstart, max(dcurrent.id) as currentend from weeks w join weeks past on w.weekminus"+shift+" = past.id join dates dcurrent on w.id=dcurrent.week join dates dpast on past.id=dpast.week where w.id>='2020W05' and w.id<='2021W06' and w.id<>'2020W53' group by w.id order by w.id;"
       
      val preparedStatement = conn.prepareStatement(timequery)
      val res = List() ++ Database.streamQuery(preparedStatement)
      res.map{row =>
        val wid = row("id").get
        val ps = row("paststart").get
        val pe = row("pastend").get
        val cs = row("currentstart").get
        val ce = row("currentend").get
    /*
        if (shift==1) {
          // Coalescing all data from both EUROStats and JHU as in the paper diagram
          val (_,objective,eqs,vars,eqCreationTime,solveTime) = VirtualSolver.solve(connector, "( ( ( ( ( ( ((( ( ( ( ( ( firstadminlevels(country='"+co+"')){id->region})[region SUM]) JOIN ( (eurostats_perregion UNION eurostats_perregion_estimated){deaths->current})) JOIN ( ( weeks{id->week})(week='"+wid+"'))) JOIN ( ( ( ( ( ( ( firstadminlevels(country='"+co+"')){id->region})[region SUM]) JOIN ((eurostats_perregion UNION eurostats_perregion_estimated){counter:=1})) JOIN ( ( weeks{id->week})(yearofweek>'2014' AND yearofweek<'2020')))[weekofyear, region SUM deaths, counter]){average:=deaths/counter})){negavg:=-1*average}){surplus:=current+negavg})[surplus]) JOIN (firstadminlevels{id->region}))[country, week SUM surplus]) DUNION[source1] ( ( ((( ( ( ((eurostats_percountry_perweek UNION eurostats_percountry_perweek_estimated){deaths->current})(country='"+co+"')) JOIN ( ( weeks{id->week})(week='"+wid+"'))) JOIN ( ( ( ( ((eurostats_percountry_perweek  UNION eurostats_percountry_perweek_estimated){counter:=1})(country='"+co+"')) JOIN ( ( weeks{id->week})(yearofweek>'2014' AND yearofweek<'2020')))[weekofyear, country SUM deaths, counter]){average:=deaths/counter})){negavg:=-1*average}){surplus:=current+negavg})[surplus] )[country, week SUM surplus]))[COAL source1]) DUNION[source2] ( ( ((dates{id->date}) join ((weeks(id='"+wid+"')){id->week})) JOIN (( (jhu_percountry(country='"+co+"'))[deaths]){deaths->surplus}))[country, week SUM surplus]))[COAL source2]", encoding)
          println(s"WithoutJHURegions;-;$co;$wid;$eqs;$vars;"+eqCreationTime+";"+solveTime+";"+objective)
          }
    */
        // Coalescing all data from both EUROStats and JHU as in the paper diagram
        print("( ( ( ( ( ( ((( ( ( ( ( ( firstadminlevels(country='"+co+"')){id->region})[region SUM]) JOIN ( (eurostats_perregion UNION eurostats_perregion_estimated){deaths->current})) JOIN ( ( weeks{id->week})(week='"+wid+"')))  JOIN ( ( ( ( ( ( ( firstadminlevels(country='"+co+"')){id->region})[region SUM]) JOIN ((eurostats_perregion UNION eurostats_perregion_estimated){counter:=1})) JOIN ( (weeks{id->week})(yearofweek>'2014' AND yearofweek<'2020')))[weekofyear, region SUM deaths, counter]){average:=deaths/counter})){negavg:=-1*average}){surplus:=current+negavg})[surplus]) JOIN (firstadminlevels{id->region}))[country, week SUM surplus]) DUNION[source1] ( ( ((( ( ( ((eurostats_percountry_perweek UNION eurostats_percountry_perweek_estimated){deaths->current})(country='"+co+"')) JOIN ( (weeks{id->week})(week='"+wid+"'))) JOIN ( ( ( ( ((eurostats_percountry_perweek  UNION eurostats_percountry_perweek_estimated){counter:=1})(country='"+co+"')) JOIN ( (weeks{id->week})(yearofweek>'2014' AND yearofweek<'2020')))[weekofyear, country SUM deaths, counter]){average:=deaths/counter})){negavg:=-1*average}){surplus:=current+negavg})[surplus] )[country, week SUM surplus]))) DUNION[source2] ( ( ( ( ( ( ( ( ( (jhu_percountry(country='"+co+"'))[deaths])) DUNION[source] ( ( ((firstadminlevels(country='"+co+"')){id->region}) JOIN (coviddata_perregion(date>='"+cs+"' and date<='"+ce+"')))[country, date SUM deaths]))[COAL source]) JOIN ((dates{id->date}) JOIN (weeks{id->week})))[country, week SUM deaths]){deaths->surplus}) DUNION[source1] ( ( ( ( ( ( ( ((firstadminlevels(country='"+co+"')){id->region}) JOIN (coviddata_perregion(date>='"+cs+"' and date<='"+ce+"'))) JOIN (dates{id->date}))[country, region, week SUM deaths]){deaths->surplus}  ) DUNION[source] ( ( ( ( ( ((firstadminlevels(country='"+co+"')){id->region}) JOIN (coviddata_perregion(date>='"+ps+"' and date<='"+pe+"'))) JOIN ((dates{id->date,week->shiftedweek}) join (weeks{weekminus"+shift+"->shiftedweek,id->week})))[country, region, week SUM cases]){surplus:=0.015*cases})[surplus]))[COAL source])[country, week SUM surplus]))))[COAL source1,source2]")
        val (_,objective,eqs,vars,eqCreationTime,solveTime) = VirtualSolver.solve(connector, "( ( ( ( ( ( ((( ( ( ( ( ( firstadminlevels(country='"+co+"')){id->region})[region SUM]) JOIN ( (eurostats_perregion UNION eurostats_perregion_estimated){deaths->current})) JOIN ( ( weeks{id->week})(week='"+wid+"')))  JOIN ( ( ( ( ( ( ( firstadminlevels(country='"+co+"')){id->region})[region SUM]) JOIN ((eurostats_perregion UNION eurostats_perregion_estimated){counter:=1})) JOIN ( (weeks{id->week})(yearofweek>'2014' AND yearofweek<'2020')))[weekofyear, region SUM deaths, counter]){average:=deaths/counter})){negavg:=-1*average}){surplus:=current+negavg})[surplus]) JOIN (firstadminlevels{id->region}))[country, week SUM surplus]) DUNION[source1] ( ( ((( ( ( ((eurostats_percountry_perweek UNION eurostats_percountry_perweek_estimated){deaths->current})(country='"+co+"')) JOIN ( (weeks{id->week})(week='"+wid+"'))) JOIN ( ( ( ( ((eurostats_percountry_perweek  UNION eurostats_percountry_perweek_estimated){counter:=1})(country='"+co+"')) JOIN ( (weeks{id->week})(yearofweek>'2014' AND yearofweek<'2020')))[weekofyear, country SUM deaths, counter]){average:=deaths/counter})){negavg:=-1*average}){surplus:=current+negavg})[surplus] )[country, week SUM surplus]))) DUNION[source2] ( ( ( ( ( ( ( ( ( (jhu_percountry(country='"+co+"'))[deaths])) DUNION[source] ( ( ((firstadminlevels(country='"+co+"')){id->region}) JOIN (coviddata_perregion(date>='"+cs+"' and date<='"+ce+"')))[country, date SUM deaths]))[COAL source]) JOIN ((dates{id->date}) JOIN (weeks{id->week})))[country, week SUM deaths]){deaths->surplus}) DUNION[source1] ( ( ( ( ( ( ( ((firstadminlevels(country='"+co+"')){id->region}) JOIN (coviddata_perregion(date>='"+cs+"' and date<='"+ce+"'))) JOIN (dates{id->date}))[country, region, week SUM deaths]){deaths->surplus}  ) DUNION[source] ( ( ( ( ( ((firstadminlevels(country='"+co+"')){id->region}) JOIN (coviddata_perregion(date>='"+ps+"' and date<='"+pe+"'))) JOIN ((dates{id->date,week->shiftedweek}) join (weeks{weekminus"+shift+"->shiftedweek,id->week})))[country, region, week SUM cases]){surplus:=0.015*cases})[surplus]))[COAL source])[country, week SUM surplus]))))[COAL source1,source2]", encoding)
        println(s"WithJHURegions;$shift;$co;$wid;$eqs;$vars;"+eqCreationTime+";"+solveTime+";"+objective)
        }
      }
    }

/*
    // cleanup
    Loader.load(connector,"countries", encoding, true)
    Loader.load(connector,"dates", encoding, true)
    Loader.load(connector,"firstadminlevels", encoding, true)
    Loader.load(connector,"weeks", encoding, true)
//--    Loader.load(connector,"coviddata_percountry", encoding, true)
    Loader.load(connector,"coviddata_perregion", encoding, true)
    Loader.load(connector,"eurostats_percountry_perweek", encoding, true)
    Loader.load(connector,"eurostats_percountry_perweek_estimated", encoding, true)
//--    Loader.load(connector,"eurostats_percountry_peryear", encoding, true)
    Loader.load(connector,"eurostats_perregion", encoding, true)
    Loader.load(connector,"eurostats_perregion_estimated", encoding, true)
    Loader.load(connector,"jhu_percountry", encoding, true)
//--    Loader.load(connector,"jhu_perregion", encoding, true)
*/
  }

}

// ((jhu_percountry(country='ES')) JOIN (dates{id->date}))[country, week SUM deaths]
// ((coviddata_perregion JOIN ((firstadminlevels{id->region})(country='ES'))) JOIN (dates{id->date}))[country, week SUM deaths]
// ((((jhu_percountry(country='ES')) JOIN (dates{id->date}))[country, week SUM deaths]) DUNION[source] (((coviddata_perregion JOIN ((firstadminlevels{id->region})(country='ES'))) JOIN (dates{id->date}))[country, week SUM deaths]))[COAL source]
// ((((jhu_percountry(country='ES')) JOIN ((dates(week='2020W05')){id->date}))[country, week SUM deaths]) DUNION[source] (((coviddata_perregion JOIN ((firstadminlevels{id->region})(country='ES'))) JOIN ((dates(week='2020W05')){id->date}))[country, week SUM deaths]))[COAL source]




