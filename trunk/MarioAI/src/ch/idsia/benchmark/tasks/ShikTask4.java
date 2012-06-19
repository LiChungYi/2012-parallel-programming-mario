package ch.idsia.benchmark.tasks;

import ch.idsia.agents.Agent;
import ch.idsia.benchmark.mario.engine.GlobalOptions;
import ch.idsia.benchmark.mario.engine.sprites.Mario;
import ch.idsia.benchmark.mario.environments.Environment;
import ch.idsia.benchmark.mario.environments.MarioEnvironment;
import ch.idsia.tools.EvaluationInfo;
import ch.idsia.tools.MarioAIOptions;
import ch.idsia.utils.statistics.StatisticalSummary;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.Random;

public class ShikTask4 implements Task
{
int[][] searchPath;
	void dumpSearchPath(){

	try{
		FileOutputStream fos = new FileOutputStream("searchpath_"+options.getReplayFile());
		ObjectOutputStream oos = new ObjectOutputStream(fos);
		oos.writeObject(searchPath);
		oos.flush();
		oos.close();
		System.err.println("Dump Search Path OK!");
	}
	catch(Exception e){
		e.printStackTrace();
	}
	}


protected Environment environment;
private Agent agent;
protected MarioAIOptions options;
private String name = getClass().getSimpleName();
private EvaluationInfo evaluationInfo;
boolean vis;
boolean[][] solution;
// LEFT,RIGHT,DOWN,JUMP,SPEED,MEOW?
boolean[][] candidateActions = {
	{false,true ,false,false,true ,false},
	{false,false,false,true ,false,false},
	{false,true ,false,true ,true ,false},
};
Random randomGenerator = new Random(514);

private Vector<StatisticalSummary> statistics = new Vector<StatisticalSummary>();

final int NUM_THREAD;
final String outputFile;
public ShikTask4(MarioAIOptions marioAIOptions)
{
	environment = new MarioEnvironment();
	vis = marioAIOptions.getParameterValue("-vis").equals("on");
	NUM_THREAD = marioAIOptions.getNumThreads();
	outputFile = marioAIOptions.getReplayFile();
	marioAIOptions.setVisualization(false); //can't be true...
    this.setOptionsAndReset(marioAIOptions);
}

Environment copy(Environment src){
	Environment dest = null;
    try{
    	ByteArrayOutputStream bos = new ByteArrayOutputStream();
    	ObjectOutputStream oos = new ObjectOutputStream(bos);
    	oos.writeObject(src);
    	oos.flush();
    	oos.close();
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
        dest = (Environment)in.readObject();
    }catch(Exception e){
    	e.printStackTrace();
    }	
    return dest;
}
public class EnvironmentGenerator{
	byte[] theByteData;
	EnvironmentGenerator(Environment src){
		try{
			ByteArrayOutputStream bos;      
			bos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(bos);
			oos.writeObject(src);
			oos.flush();
			oos.close();

			theByteData = bos.toByteArray();
		}
		catch(Exception e){
			e.printStackTrace();
		}       
	}       
	Environment copyEnvironment(){
		Environment dest = null;
		try{
			ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(theByteData));
			dest = (Environment)in.readObject();
			in.close();
		}
		catch(Exception e){
			e.printStackTrace();
		}
		return dest;
	}
}


boolean checkSolvable( Environment env ) {
	byte[][] levelScene = env.getLevelSceneObservationZ(2);
	for ( int i=0; i<levelScene.length; i++ ) {
		for ( int j=0; j<levelScene[i].length; j++ ) {
			if ( levelScene[i][j] == 2 ) levelScene[i][j] = 0;
			if ( levelScene[i][j] == -60 ) levelScene[i][j] = 1;
		}
	}
	int n = levelScene.length, mx = env.getMarioEgoPos()[0], my = env.getMarioEgoPos()[1];
	for ( int i=n-2; i>=my; i-- ) {
		for ( int j=1; j<n; j++ ) {
			if ( levelScene[j][i]==1 || (levelScene[j-1][i]==1 && levelScene[j][i+1]==1) ) levelScene[j][i] = 1;
			else levelScene[j][i] = 0;
		}
	}
/*
	for ( int i=0; i<levelScene.length; i++ ) {
		for ( int j=0; j<levelScene[i].length; j++ ) {
			System.err.printf("%3d ",(int)levelScene[i][j]);
		}
		System.err.printf("\n");
	}
*/
	return levelScene[mx][my]==0;
}
class CopyEnvThread extends Thread {
	EnvironmentGenerator envGen;
	Environment env;
	public CopyEnvThread( EnvironmentGenerator _envGen ) {
		envGen = _envGen;
	}
	public void run() {
		env = envGen.copyEnvironment();
	}
}
private boolean dfs(int lv, Environment env){
	float nowX = env.getMarioFloatPos()[0];
	System.err.printf("%d: nowX=%.2f\n",lv,nowX);
	if ( !checkSolvable(env) ) return false;
    if ( env.isLevelFinished() ) {
		solution = new boolean[lv][Environment.numberOfKeys];
		return true;
	}
	EnvironmentGenerator envGen = new EnvironmentGenerator(env);
	for ( int i=0; i<16||lv==0; i+=NUM_THREAD ) {
		CopyEnvThread[] copyEnvThread = new CopyEnvThread[NUM_THREAD];
		for ( int t=0; t<NUM_THREAD; t++ ) {
			copyEnvThread[t] = new CopyEnvThread(envGen);
			//copyEnvThread[t].setPriority(Thread.MAX_PRIORITY);
			copyEnvThread[t].start();
		}
		for ( int t=0; t<NUM_THREAD; t++ ) {
			try { copyEnvThread[t].join(); } catch ( InterruptedException e ) { e.printStackTrace(); }
			Environment nextEnv = copyEnvThread[t].env;
			ArrayList<boolean[]> acts = new ArrayList<boolean[]>();
			boolean bye = false;
			int sumWeight = 0;
			int[] probWeight = new int[candidateActions.length];
			for ( int j=0; j<candidateActions.length; j++ ) {
				probWeight[j] = 1<<randomGenerator.nextInt(6);
				sumWeight += probWeight[j];
			}
			for ( int j=0; j<16 && !bye; j++ ) {
				int actionSeed = randomGenerator.nextInt(sumWeight), actionID = 0, nowWeight = 0;
				while ( actionSeed >= nowWeight + probWeight[actionID] ) {
					nowWeight += probWeight[actionID];
					actionID += 1;
				}
				acts.add(candidateActions[actionID]);


				// add
				int lastXint = (int)nextEnv.getMarioFloatPos()[0], lastYint = (int)nextEnv.getMarioFloatPos()[1];
				//
				nextEnv.tick();
				// add search path
				int nxtXint = (int)nextEnv.getMarioFloatPos()[0];
				int nxtYint = (int)nextEnv.getMarioFloatPos()[1];
				if (lastXint >= 0 && lastYint >= 0) {
					int xdif = nxtXint - lastXint; xdif = xdif>0? xdif: -xdif;
					int ydif = nxtYint - lastYint; ydif = ydif>0? ydif: -ydif;
					float lim = (xdif>ydif? xdif: ydif), ratio = 1f/lim;
					for(float s = ratio; s <= 1.0; s+= ratio) {
						int xx = (int)(lastXint + (nxtXint-lastXint)*s);
						int yy = (int)(lastYint + (nxtYint-lastYint)*s);
						if (xx < searchPath.length && xx >= 0 && yy < searchPath[0].length && yy >= 0) {
							int v = 0 + (((searchPath[xx][yy]&255)+1)&255);
							searchPath[xx][yy] = v;
						}
					}
				}
				lastXint = nxtXint; lastYint = nxtYint;
				//


				nextEnv.performAction(candidateActions[actionID]);
				if ( nextEnv.getMarioStatus() == Mario.STATUS_DEAD ) bye = true;
				if ( nextEnv.getEvaluationInfo().marioMode != 2 ) bye = true;
			}
			if ( nextEnv.getMarioFloatPos()[0]<=nowX ) bye = true;
			if ( bye ) continue;
			if ( dfs(lv+acts.size(),nextEnv) ) {
				for ( int j=0; j<acts.size(); j++ ) solution[lv+j] = acts.get(j);
				for ( int tt=t+1; tt<NUM_THREAD; tt++ ) copyEnvThread[tt].stop();
				return true;
			}
		}
	}
	return false;
}

/**
 * @param repetitionsOfSingleEpisode
 * @return boolean flag whether controller is disqualified or not
 */
public boolean runSingleEpisode(final int repetitionsOfSingleEpisode)
{
	List<boolean[]> trace = new ArrayList<boolean[]>();
    for (int r = 0; r < repetitionsOfSingleEpisode; ++r)
    {
        this.reset();

		searchPath = new int[4096][256];
		for (int i = 0; i < 4096; i++)
			for(int j=0;j<256;j++)
				searchPath[i][j] = 0;

		dfs(0,copy(environment));
		dumpSearchPath();


        //replay
        if(vis) {
			options.setVisualization(vis);
        	environment.reset(options);
		}
		for(int i=0;!environment.isLevelFinished();++i){
			//System.err.println(i + ":" + solution[i][0] + solution[i][1] + solution[i][2] + solution[i][3] + solution[i][4] + solution[i][5]);
			environment.tick();
			environment.performAction(solution[i]);
			trace.add(solution[i]);
		}
		System.err.printf("core = %d\n",Runtime.getRuntime().availableProcessors());
        //output trace
        try{
        	FileOutputStream fos = new FileOutputStream(outputFile);
        	ObjectOutputStream oos = new ObjectOutputStream(fos);
        	oos.writeObject(trace);
        	oos.flush();
        	oos.close();
        }catch(Exception e){
        	e.printStackTrace();
        }
        environment.closeRecorder(); //recorder initialized in environment.reset
        environment.getEvaluationInfo().setTaskName(name);
        this.evaluationInfo = environment.getEvaluationInfo().clone();
	}
    return true;
}

public Environment getEnvironment()
{
    return environment;
}

public int evaluate(Agent controller)
{
    return 0;
}

public void setOptionsAndReset(MarioAIOptions options)
{
    this.options = options;
    reset();
}

public void setOptionsAndReset(final String options)
{
    this.options.setArgs(options);
    reset();
}

public void doReplay(){
	List<boolean[]> trace = new ArrayList<boolean[]>();
	options.setVisualization(true);
	environment.reset(options);
    try{
    	ObjectInputStream in = new ObjectInputStream(new FileInputStream("output"));
    	trace = (List<boolean[]>)in.readObject();
        for(int i=0;i<trace.size() && !environment.isLevelFinished();++i){
        	environment.tick();
        	environment.performAction(trace.get(i));
        }
    }catch(Exception e){
    	e.printStackTrace();
    }
}

public void doEpisodes(int amount, boolean verbose, final int repetitionsOfSingleEpisode)
{
    for (int j = 0; j < EvaluationInfo.numberOfElements; j++)
    {
        statistics.addElement(new StatisticalSummary());
    }
    for (int i = 0; i < amount; ++i)
    {
        this.reset();
        this.runSingleEpisode(repetitionsOfSingleEpisode);
        if (verbose)
            System.out.println(environment.getEvaluationInfoAsString());

        for (int j = 0; j < EvaluationInfo.numberOfElements; j++)
        {
            statistics.get(j).add(environment.getEvaluationInfoAsInts()[j]);
        }
    }

    System.out.println(statistics.get(3).toString());
}

public boolean isFinished()
{
    return false;
}

public void reset()
{
    agent = options.getAgent();
    environment.reset(options);
    agent.reset();
    agent.setObservationDetails(environment.getReceptiveFieldWidth(),
            environment.getReceptiveFieldHeight(),
            environment.getMarioEgoPos()[0],
            environment.getMarioEgoPos()[1]);
}

public String getName()
{
    return name;
}

public void printStatistics()
{
    System.out.println(evaluationInfo.toString());
}

public EvaluationInfo getEvaluationInfo()
{
//    System.out.println("evaluationInfo = " + evaluationInfo);
    return evaluationInfo;
}

}

//            start timer
//            long tm = System.currentTimeMillis();

//            System.out.println("System.currentTimeMillis() - tm > COMPUTATION_TIME_BOUND = " + (System.currentTimeMillis() - tm ));
//            if (System.currentTimeMillis() - tm > COMPUTATION_TIME_BOUND)
//            {
////                # controller disqualified on this level
//                System.out.println("Agent is disqualified on this level");
//                return false;
//            }
