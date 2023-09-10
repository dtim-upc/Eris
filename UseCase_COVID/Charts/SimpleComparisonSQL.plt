reset
fontSpec(s) = sprintf("Verdana, %d", s)
set terminal pngcairo enhanced truecolor size 2048, 1080
set colorsequence classic
set border 3
#set tmargin 1
set lmargin 17
#----------------------------------------------Fonts
set key font ",17"
#set title font ",25"
set ylabel font ",30"
set xlabel font ",30"
set ytics font ",25"
set xtics font ",25"
#----------------------------------------------Key&Title
set key autotitle columnhead
set key maxrows 6
set key inside right
#set title 'Running average of weekly errors between cases reported by country and the aggregated of regions'
#set title offset 0,-4
#----------------------------------------------Axis
set logscale y
set yrange [1e-10:100]
set ylabel 'Average squared difference'
set ylabel offset -5,0
set ytics nomirror
set xlabel 'Weeks'
set xlabel offset 0,-4
set xtics rotate by 45
set xtics nomirror
set xtics right
#---------------------------------------------Input-Output
set datafile separator ';'
set output 'SimpleComparisonSQL.png' 
plot 'SimpleComparisonSQL.csv' using "DE":xticlabels(int($0)%2 == 0 ? stringcolumn(1) : '') with lines linewidth 4 dt 1, \
		  ''   using "ES":xticlabels(int($0)%2 == 0 ? stringcolumn(1) : '') with lines linewidth 4 dt 2, \
		  ''   using "IT":xticlabels(int($0)%2 == 0 ? stringcolumn(1) : '') with lines linewidth 4 dt 3, \
		  ''   using "NL":xticlabels(int($0)%2 == 0 ? stringcolumn(1) : '') with lines linewidth 4 dt 4, \
		  ''   using "SE":xticlabels(int($0)%2 == 0 ? stringcolumn(1) : '') with lines linewidth 4 dt 5, \
		  ''   using "UK":xticlabels(int($0)%2 == 0 ? stringcolumn(1) : '') with lines linewidth 4 dt 6 lc "brown"

