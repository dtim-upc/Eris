reset
fontSpec(s) = sprintf("Verdana, %d", s)
set terminal pngcairo enhanced truecolor size 1080, 1080
set colorsequence classic
#set border 3
set tmargin 5
set lmargin at screen 0.1
#set rmargin at screen 0.95
#----------------------------------------------Fonts
set key font ",25"
#set ylabel font ",30"
#set xlabel font ",30"
set ytics font ",25"
set xtics font ",25"
set format x "10^{%1T}
set format y "10^{%1T}
#----------------------------------------------Axis
#set xrange [0.1:100]
#set yrange [0:0.4]
set logscale x 10
set logscale y 10
#----------------------------------------------Key&Title
#set key autotitle columnhead
#set key maxrows 6
set key inside left
set title font ",60"
#---------------------------------------------Input-Output
set datafile separator ','
#set view map
#set dgrid3d
#set pm3d interpolate 10,10
do for [q in "T1 T2 T3 T4 T5"] {
  set title q offset 0, -3
  inputname = 'output\distPivot_' . q . '.csv'
  outputname = 'charts\distortionVariation_' . q . '.jpg'
  set output outputname
  plot inputname using "x":"y00" title "0% nulls" with lines linewidth 4 dt 1 ,\
              '' using "x":"y05" title "5% nulls" with lines linewidth 4 dt 2 ,\
              '' using "x":"y10" title "10% nulls" with lines linewidth 4 dt 3 ,\
              '' using "x":"y20" title "20% nulls" with lines linewidth 4 dt 4 ,\
              '' using "x":"y40" title "40% nulls" with lines linewidth 4 dt 5
  }

