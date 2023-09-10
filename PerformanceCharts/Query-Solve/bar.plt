reset
fontSpec(s) = sprintf("Verdana, %d", s)
set terminal pngcairo enhanced truecolor size 1080, 1080 #2048, 1080
set colorsequence classic
#set border 3
set tmargin 5
set lmargin at screen 0.1
#set rmargin at screen 0.95
#----------------------------------------------Fonts
set key font ",25"
#set ylabel "x-units" font ",80"
#set xlabel "y-units" font ",40"
set ytics font ",25"
set xtics font ",25"
set format y "10^{%1T}"
#----------------------------------------------Axis
#set xrange [0:1000]
set yrange [1:1000000]
#set logscale x 10
set logscale y 100
#----------------------------------------------Key&Title
#set key autotitle columnhead
#set key maxrows 6
#set key inside left
set key at screen 0.60, 0.85
set title font ",60"
#---------------------------------------------Input-Output
set datafile separator ','
#set errorbars fullwidth
set bars 2
#set boxwidth 1 relative
#show boxwidth
set style data histogram
set style histogram errorbars gap 0 lw 2
set style fill solid border lt -1
list = "Partitioning NF2"
item(n) = word(list,n)
#  do for [f in "0none 1half 2onlyr 3all"] {
  do for [f in "3all"] {
    do for [q in "q1 q2 q3 q4 q5 q6 q7"] {
      set title q offset 0, -3
      inputname = 'output\eval_' . q . '_' . f . '.csv'
      outputname = 'charts\evaluation_performance_' . q . '_' . f . '.jpg'
      set output outputname
#      plot for [COL==0:1:1] inputname skip 1 using COL*5+2:COL*5+3:xtic(1) title item(COL+1) # Taking Avg-Stdev
      plot for [COL=0:1:1] inputname skip 1 using COL*5+4:COL*5+5:COL*5+6:xtic(1) title item(COL+1) # Taking median-min-max
      }
    }

#  do for [f in "0none 1half 2onlyr 3all"] {
  do for [f in "3all"] {
    do for [q in "T1 T2 T3 T4 T5"] {
      set title q
      inputname = 'output\solve_' . q . '_' . f . '.csv'
      outputname = 'charts\eq_generation_performance_' . q . '_' . f . '.jpg'
      set output outputname
#      plot for [COL==0:1:1] inputname skip 1 using COL*5+2:COL*5+3:xtic(1) title item(COL+1) # Taking Avg-Stdev
      plot for [COL=0:1:1] inputname skip 1 using COL*5+4:COL*5+5:COL*5+6:xtic(1) title item(COL+1) # Taking median-min-max
      }
    unset key
    unset ylabel
    }

set lmargin at screen 0.1
set style histogram rowstacked
set ylabel
set yrange [0:1]
set format y "%0.1f"
unset logscale y
set key outside right
i = 1
do for [e in "partitioning nf2_sparsev"] {
  set title item(i)
#  do for [f in "0none 1half 2onlyr 3all"] {
  do for [f in "3all"] {
    do for [s in "10000"] {
      inputname = 'output\' . s . '_' . e . '_' . f . '.csv'
      outputname = 'charts\percentages' . s . '_' . f . '_' . e . '.jpg'
      set output outputname
      plot inputname using 2 title "eqGen", '' using 3:xtic(1) title "OSQP"
      }
    }
  i = i+1
  } 

