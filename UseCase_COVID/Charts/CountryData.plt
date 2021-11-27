reset
fontSpec(s) = sprintf("Verdana, %d", s)
set terminal pngcairo enhanced truecolor size 2048, 1080
set colorsequence classic
set border 3
#set tmargin 1
set lmargin 14
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
set key inside right
#set title 'Running average of weekly errors per country without considering regional data'
#set title offset 0,-4
#----------------------------------------------Axis
set yrange [0:0.015]
set ylabel 'Average squared error'
set ylabel offset -3.5,0
set ytics nomirror
set xlabel 'Weeks'
set xlabel offset 0,-3.5
set xtics rotate by 45
set xtics nomirror
set xtics right
#---------------------------------------------Input-Output
set datafile separator ';'
set output 'CountryData.png' 
# Weeks are only shown in even rows 
# An arrow is added to week 20200713
plot 'CountryData.csv' using "DE":xticlabels(floor($0/2) != $0/2? ($1!=20200713?sprintf("%d",$1):"->20200713"): NaN) with lines linewidth 4 dt 1, \
		  ''   using "ES":xticlabels(floor($0/2) != $0/2? ($1!=20200713?sprintf("%d",$1):"->20200713"): NaN) with lines linewidth 4 dt 2, \
		  ''   using "IT":xticlabels(floor($0/2) != $0/2? ($1!=20200713?sprintf("%d",$1):"->20200713"): NaN) with lines linewidth 4 dt 3, \
		  ''   using "NL":xticlabels(floor($0/2) != $0/2? ($1!=20200713?sprintf("%d",$1):"->20200713"): NaN) with lines linewidth 4 dt 4, \
		  ''   using "SE":xticlabels(floor($0/2) != $0/2? ($1!=20200713?sprintf("%d",$1):"->20200713"): NaN) with lines linewidth 4 dt 5, \
		  ''   using "UK":xticlabels(floor($0/2) != $0/2? ($1!=20200713?sprintf("%d",$1):"->20200713"): NaN) with lines linewidth 4 dt 6 lc "brown"
