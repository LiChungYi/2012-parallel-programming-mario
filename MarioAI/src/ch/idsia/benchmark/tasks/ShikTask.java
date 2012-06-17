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

public class ShikTask implements Task
{
protected Environment environment;
private Agent agent;
protected MarioAIOptions options;
private String name = getClass().getSimpleName();
private EvaluationInfo evaluationInfo;
boolean vis;
boolean[][] solution;
// LEFT,RIGHT,DOWN,JUMP,SPEED,MEOW?
boolean[][] candidateActions = {
	//{false,true ,false,false ,true ,false},
	{false,true ,false,false,true ,false},
	{false,false,false,true ,false,false}
	//{false,true ,false,true ,true ,false},
	//{false,true ,false,true ,false,false},
	//{false,true ,false,false,true ,false},
	//{false,false,false,true ,true ,false}};
};

private Vector<StatisticalSummary> statistics = new Vector<StatisticalSummary>();

public ShikTask(MarioAIOptions marioAIOptions)
{
	environment = new MarioEnvironment();
	vis = marioAIOptions.getParameterValue("-vis").equals("on");
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

private boolean dfs(int lv, float far, int cnt, Environment env){
	float nowX = env.getMarioFloatPos()[0];
	//System.err.println(lv + "," + far + "," + cnt + " : " + nowX);
	System.err.printf("%d: far=%.2f, cnt=%d, nowX=%.2f\n",lv,far,cnt,nowX);
    if ( env.getMarioStatus() == Mario.STATUS_DEAD ) return false;
    if ( env.getEvaluationInfo().marioMode != 2 ) return false;
    if ( env.isLevelFinished() ) {
		solution = new boolean[lv][Environment.numberOfKeys];
		return true;
	}
	if ( nowX > far ) {
		cnt = 0;
		far = nowX;
	} else {
		cnt = cnt + 1;
	}
	if ( cnt>3 ) return false;
	for ( boolean act[] : candidateActions ) {
		if ( !env.isMarioAbleToShoot() && act[Mario.KEY_SPEED] ) continue; 
		Environment nextEnv = copy(env);
		nextEnv.tick();
		nextEnv.performAction(act);
		if ( dfs(lv+1,far,cnt,nextEnv) ) {
			solution[lv] = act;
			return true;
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
		dfs(0,0,0,copy(environment));
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
        //output trace
        try{
        	FileOutputStream fos = new FileOutputStream("output");
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
