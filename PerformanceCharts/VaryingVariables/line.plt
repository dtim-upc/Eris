reset
fontSpec(s) = sprintf("Verdana, %d", s)
set terminal pngcairo enhanced truecolor size 1080, 1080
set colorsequence classic
#set border 3
set tmargin 5
set lmargin at screen 0.2
#set rmargin at screen 0.95
#----------------------------------------------Fonts
set key font ",25"
#set ylabel font ",30"
#set xlabel font ",30"
set ytics font ",25"
set xtics font ",20"
#----------------------------------------------Axis
#set xrange [0.1:100]
#set yrange [500:20000]
#set logscale x 10
#set logscale y 10
set format y "%.1t*10^{%1T}"
#----------------------------------------------Key&Title
#set key autotitle columnhead
#set key maxrows 6
set key at screen 0.68, 0.85
set title font ",60"
#---------------------------------------------Input-Output
set datafile separator ','
#set view map
#set dgrid3d
#set pm3d interpolate 10,10

do for [s in "1000"] {
  do for [q in "T1 T2 T3 T4 T5"] {
    set title q offset 0, -3
    inputname = 'output\variying_variablesPivot_' . s . '_' . q . '.csv'
    outputname = 'charts\varyingVariables_total_' . s . '_' . q . '.jpg'
    set output outputname
    plot inputname using "x":"total_p" title "Partitioning" with lines linewidth 4 dt 1 ,\
                '' using "x":"total_nf2" title "NF2" with lines linewidth 4 dt 2 ,\
    }
  }

#    outputname = 'charts\query_' . s . '_' . q . '.png'
#    set output outputname
#    plot inputname using "x":"query_p" title "Partitioning" with lines linewidth 4 dt 1 ,\
#                '' using "x":"query_nf2" title "NF2" with lines linewidth 4 dt 2 ,\
#    outputname = 'charts\solve_' . s . '_' . q . '.png'
#    set output outputname
#    plot inputname using "x":"solve_p" title "Partitioning" with lines linewidth 4 dt 1 ,\
#                '' using "x":"solve_nf2" title "NF2" with lines linewidth 4 dt 2 ,\
