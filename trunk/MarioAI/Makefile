all:
	rm -f sources_list.txt
	find ./src -name "*.java" > sources_list.txt
	-mkdir bin
	javac -d bin -encoding latin1 -classpath "lib/asm-all-3.3.jar;lib/jdom.jar; lib/junit-4.8.2.jar;." @sources_list.txt
	-cp -r resources bin/ch/idsia/benchmark/mario/engine
run:
	java -classpath "lib/asm-all-3.3.jar;lib/jdom.jar;lib/junit-4.8.2.jar;bin" ch.idsia.scenarios.Main -ag ch.idsia.agents.controllers.ForwardAgent -ld 0

