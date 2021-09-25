reset
fontSpec(s) = sprintf("Verdana, %d", s)
set terminal pngcairo enhanced truecolor size 2048, 1080
set colorsequence classic
set border 3
#set tmargin 1
set lmargin 12
set bmargin 4
#----------------------------------------------Fonts
set key font ",25"
#set title font ",25"
set ylabel font ",30"
set xlabel font ",30"
set ytics font ",25"
set xtics font ",25"
#----------------------------------------------Key&Title
set key autotitle columnhead
set key maxrows 6
set key inside left
#set title 'Running average of weekly errors per country without considering regional data'
#set title offset 0,-4
#----------------------------------------------Axis
set yrange [0:0.10]
set ylabel 'Average squared error'
set ylabel offset -2.5,0
set ytics nomirror
set xlabel 'Lag between case and death reporting (in weeks)'
set xtics nomirror
#---------------------------------------------Input-Output
set datafile separator ';'
set output 'Alignements.png'
plot 'Alignements.csv' using "DE":xticlabels(1) with lines linewidth 4 dt 1, \
		  ''   using "ES":xticlabels(1) with lines linewidth 4 dt 2, \
		  ''   using "IT":xticlabels(1) with lines linewidth 4 dt 3, \
		  ''   using "NL":xticlabels(1) with lines linewidth 4 dt 4, \
		  ''   using "SE":xticlabels(1) with lines linewidth 4 dt 5, \
		  ''   using "UK":xticlabels(1) with lines linewidth 4 dt 6 lc "brown"

