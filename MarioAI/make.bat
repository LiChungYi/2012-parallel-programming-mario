mkdir bin
dir src\*.java /s /b > sources_list.txt
javac -d bin -encoding latin1 -classpath lib/asm-all-3.3.jar;lib/jdom.jar;lib/junit-4.8.2.jar;.;src @sources_list.txt
copy resources bin/ch/idsia/benchmark/mario/engine
