/*
 * Copyright (c) 2009-2010, Sergey Karakovskiy and Julian Togelius
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *  Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *  Neither the name of the Mario AI nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

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

/**
 * Created by IntelliJ IDEA.
 * User: Sergey Karakovskiy,
 * sergey@idsia.ch
 * Date: Mar 14, 2010 Time: 4:47:33 PM
 */

public class TmtTask implements Task
{
protected Environment environment;
private Agent agent;
protected MarioAIOptions options;
private long COMPUTATION_TIME_BOUND = 42;
private String name = getClass().getSimpleName();
private EvaluationInfo evaluationInfo;

private Vector<StatisticalSummary> statistics = new Vector<StatisticalSummary>();
boolean vis;
Random random = new Random(0);

public TmtTask(MarioAIOptions marioAIOptions)
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

void dumpPath(ArrayList<boolean[]> surePathGivenEnvironment){
	//output trace
	List<boolean[]> trace = new ArrayList();
	for(int i = 0; i < surePathGivenEnvironment.size(); ++i)
		trace.add(surePathGivenEnvironment.get(i));

	try{
		FileOutputStream fos = new FileOutputStream("output");
		ObjectOutputStream oos = new ObjectOutputStream(fos);
		oos.writeObject(trace);
		oos.flush();
		oos.close();
		System.err.println("Dump File OK!");
	}
	catch(Exception e){
		e.printStackTrace();
	}
}

// LEFT,RIGHT,DOWN,JUMP,SPEED,MEOW?

ArrayList<ArrayList<ArrayList<boolean[]> > > candidateActions;

void initCandidateActions() {
	boolean[] RIGHT_SPEED = {false, true, false, false, true, false},
			JUMP_SPEED = {false, false, false, true, true, false},
			RIGHT = {false, true, false, false, false, false},
			LEFT_SPEED = {true, false, false, false, true, false};
	ArrayList<boolean[]> RS = new ArrayList<boolean[]>(),
		JS = new ArrayList<boolean[]>(),
		R = new ArrayList<boolean[]>(),
		LS = new ArrayList<boolean[]>();
	RS.add(RIGHT_SPEED);
	JS.add(JUMP_SPEED);
	R.add(RIGHT);
	LS.add(LEFT_SPEED);

	candidateActions = new ArrayList<ArrayList<ArrayList<boolean[]> > >();
	for (int i = 0; i < 4100 * 15; i++) {
		ArrayList<ArrayList<boolean[]> > r = new ArrayList<ArrayList<boolean[]> >();
		//r.add(RS);
		//r.add(JS);
		//r.add(R);
		//r.add(LS);
		candidateActions.add(r);
	}
}

ArrayList<ArrayList<boolean[]> > oP;
ArrayList<Environment> oE;
float targetPosX;
final int limitSolutions = 6, limitKeepSolutions = 3;
int foundSolution = 0;

final int limitDfsDepth = 100, LEVELFINISHED = 100000;
ArrayList<ArrayList<boolean[]> > mDfsPath;
Environment[] mDfsEnv = new Environment[limitDfsDepth];
int[] mDfsFitness = new int[limitDfsDepth];
int[] mDfsFailedCount = new int[limitDfsDepth];

void initDfsPath() {
	mDfsPath = new ArrayList<ArrayList<boolean[]> >();
	for (int i = 0; i < limitDfsDepth; i++)
		mDfsPath.add(new ArrayList<boolean[]>());
}

ArrayList<boolean[]> getRandomPath(int len) {
	final boolean[][] basicActions = {
			{false, true, false, false, true, false},
			{false, false, false, true, true, false},
			{false, true, false, true, false, false},
			{false, true, false, true, true, false},
			{true, false, false, false, true, false},
			{false, false, false, false, false, false}
			};
	ArrayList<boolean[]> ret = new ArrayList<boolean[]>();
	for (int i = 0; i < len; ) {
		int rnd = random.nextInt(6);
		int k = random.nextInt(10)+1;
		if(rnd == 5) k = 1;
		if(rnd == 4 && random.nextInt(2)==0) { continue; }
		for(int j=0;j<k;j++,i++)
			ret.add(basicActions[rnd]);
	}
	return ret;
}

int TRACEBACK = 1, TRACECRITERIA = 10, TRACEKEEPLIMIT = 2;
int MAGICLENGTH = 95;
boolean dfsOkToShrink = false;

boolean checkRealOK(Environment copyEnv) {
	/*for(int i = 0; i < 5; i++) {
		copyEnv.tick();
		if (copyEnv.getMarioStatus() == Mario.STATUS_DEAD)
			return false;
	}*/
	return true;
}
int testFitness(ArrayList<boolean[]> testPath, Environment copyEnv) {
	for(int i=0;i<testPath.size();i++) {
		copyEnv.performAction(testPath.get(i));
		copyEnv.tick();
		if (copyEnv.getMarioStatus() == Mario.STATUS_DEAD) return -LEVELFINISHED;
	}
	return calculateFitness(copyEnv);
}

void go(int lev, ArrayList<boolean[]> nowPath, Environment nowEnv) {
	
	if (lev == limitDfsDepth) return;

	mDfsPath.set(lev, (ArrayList) nowPath.clone());
	mDfsEnv[lev] = copy(nowEnv);
	mDfsFitness[lev] = calculateFitness(nowEnv);
	
	float nowX = nowEnv.getMarioFloatPos()[0];
	int nowXint = (int)nowX;
	int nowYint = (int)nowEnv.getMarioFloatPos()[1];
	int nowIdx = nowXint * 15 + nowYint;

	int state[] = nowEnv.getMarioState();
	int nowFitness = mDfsFitness[lev];

	if (nowPath.size() - mDfsPath.get(0).size() >= MAGICLENGTH) return;

	if (lev >= TRACEBACK && nowFitness > mDfsFitness[lev - TRACEBACK] + TRACECRITERIA) {
		int prevXint = (int)(mDfsEnv[lev- TRACEBACK].getMarioFloatPos()[0]);
		int prevYint = (int)(mDfsEnv[lev- TRACEBACK].getMarioFloatPos()[1]);
		int prevIdx = prevXint * 15 + prevYint;
		int prevCandidateSize = candidateActions.get(prevIdx).size();
		ArrayList<boolean[]> actionPath = new ArrayList<boolean[]>(); 
		for (int i = mDfsPath.get(lev-TRACEBACK).size(); i < nowPath.size(); i++)
			actionPath.add(nowPath.get(i));
		if (prevCandidateSize < TRACEKEEPLIMIT) candidateActions.get(prevIdx).add(actionPath);
		else candidateActions.get(prevIdx).set(random.nextInt(TRACEKEEPLIMIT), actionPath);
	}

	if(!dfsOkToShrink)
	for (int i=5;i<16 && i<=lev;i++) {
		int cnt = 0;
		if(nowFitness < mDfsFitness[lev-i])
			cnt++;
		if (cnt > 2) return ;
	}

	if(nowEnv.getEvaluationInfo().marioMode != 2) return;

	System.err.println("Here we get pos at " + ((int)nowX) + " [Fitness = " + nowFitness + "] at lev = " + lev + " [len = " +
			(nowPath.size() - mDfsPath.get(0).size()) + "] total = " + nowPath.size());
	if ( foundSolution >= limitSolutions )
		return;

	if ( nowEnv.getMarioStatus() == Mario.STATUS_DEAD ) {
		System.err.println("DEAD!");
		return;
	}

	if ( nowEnv.isLevelFinished() ) { /* Win!! */
		System.err.println("level finished!!");
		oP.add((ArrayList<boolean[]>) nowPath.clone());
		oE.add(copy(nowEnv));
		foundSolution = LEVELFINISHED;
		return;
	}

	if ( nowX >= targetPosX && (state[2] == 1 || state[3] == 1)) { /* isMarioOnGround || isAbleToJump */

		if (checkRealOK(copy(nowEnv)) == false) return;

		System.err.println("good job!");
		oP.add((ArrayList<boolean[]>) nowPath.clone());
		oE.add(copy(nowEnv));
		foundSolution++;
		//returncnt = (lev/5 < 3? lev/5: 3);
		return;
	}

	int psize = candidateActions.get(nowIdx).size();
	ArrayList<ArrayList<boolean[]> > p = (ArrayList) candidateActions.get(nowIdx).clone();

	int bestFitness = 0;
	ArrayList<boolean[]> examplePath = null;
	for (int i = 0; i < 50 && examplePath == null; i++) {
		ArrayList<boolean[]> testPath = getRandomPath(20);
		int f = testFitness(testPath, copy(nowEnv));
		if(f>0 && (examplePath == null || bestFitness < f)) {
			f = bestFitness;
			examplePath = testPath;
		}
	}
	if(examplePath != null) p.add(examplePath);
	for (int i = 0; i<50 && p.size() < 5; i++) {
		ArrayList<boolean[]> testPath = getRandomPath(20);
		int f = testFitness(testPath, copy(nowEnv));
		if (f > bestFitness - 5 && f > 0 && p.size() < 5) {
			p.add(testPath);
		}
	}

	for (int i = 0; i < p.size(); i++) {
		Environment nxtEnv = copy(nowEnv);
		
		int curPathSize = nowPath.size();
		for (int j = 0; j < p.get(i).size(); j++) {
			nowPath.add(p.get(i).get(j));
			nxtEnv.performAction(p.get(i).get(j));
			nxtEnv.tick();
		}
		
		go(lev+1, nowPath, nxtEnv);
		for (int j = 0; j < p.get(i).size(); j++) {
			nowPath.remove(nowPath.size()-1);
		}
		if (foundSolution >= limitSolutions)
			return;
	}

}

int calculateFitness(Environment env) {
	/*int ret = 0;
	ret = (int)(env.getEvaluationInfo().computeDistancePassed() * 5);
	//ret += (int)(env.getMarioFloatPos()[1] * 100);
	double timeLeft = env.getEvaluationInfo().timeLeft;
	double timeSpent = env.getEvaluationInfo().timeSpent;
	ret += (int)((timeLeft / (timeLeft + timeSpent)) * 200);
	if (env.getEvaluationInfo().marioMode == 2) ret += 8000;
	if (env.getEvaluationInfo().marioMode == 1) ret += 1000;
	return ret;*/
	
	return env.getEvaluationInfo().computeBasicFitness();
}

ArrayList<boolean[]> solve(Environment env) {
	final float searchFrameLength = 157.0f;
	float now = 0.0f;
	boolean solved = false;

	env.tick();
	Environment initEnvironment = copy(env);

	//init candidate Actions
	initCandidateActions();

	ArrayList<ArrayList<boolean[]> > candidatePaths = new ArrayList<ArrayList<boolean[]> >();
	ArrayList<Environment> candidateEnvironments = new ArrayList<Environment>();
	
	candidatePaths.add(new ArrayList<boolean[]>());
	candidateEnvironments.add(env);
	

	
	for (now = 0.0f; !solved && !candidatePaths.isEmpty(); now += searchFrameLength) {
		System.err.println("Starting Search at " + now + "; num of path = " + candidatePaths.size());
		ArrayList<ArrayList<boolean[]> > tmpPaths = new ArrayList<ArrayList<boolean[]> >();
		ArrayList<Environment> tmpEnvironments = new ArrayList<Environment>();

		initDfsPath();
		oP = tmpPaths;
		oE = tmpEnvironments;
		targetPosX = now + searchFrameLength;

		for (int i = 0; i < candidatePaths.size(); i++) {
			ArrayList<boolean[]> thisPath = candidatePaths.get(i);
			Environment thisEnvironment = copy(candidateEnvironments.get(i));
			foundSolution = 0;
			
			go(0, thisPath, thisEnvironment);

			if (foundSolution >= LEVELFINISHED) break;
		}

		if (oP.size() == 0) {
			MAGICLENGTH += 30;
			System.err.println("Oh My GOD... Restarting...with allowing " + MAGICLENGTH + " each frame");
			dfsOkToShrink = true;
			now -= searchFrameLength;
			if (now < 0) now = 0;

			if (MAGICLENGTH >= 150) {
				//force back
				for (int i=0;i<candidatePaths.size();i++) {
					ArrayList<boolean[]> nowPath = candidatePaths.get(i);
					int siz = nowPath.size();
					for (int j = 0; j < 15 && siz > 0; j++) {
						siz--;
						nowPath.remove(siz);
					}
					Environment nowEnv = copy(initEnvironment);
					for (int j = 0; j < nowPath.size(); j++) {
						nowEnv.performAction(nowPath.get(j));
						nowEnv.tick();
					}
					candidateEnvironments.set(i, nowEnv);
				}
				MAGICLENGTH = 95;
			}

			continue;
		}

		dfsOkToShrink = false;
		MAGICLENGTH = 95;

		int[] fitness = new int[oP.size()];
		boolean[] used = new boolean[oP.size()];
		for (int i = 0; i < oE.size(); i++) {
			fitness[i] = calculateFitness(oE.get(i));
		}
		
		candidatePaths = new ArrayList<ArrayList<boolean[]> >();
		candidateEnvironments = new ArrayList<Environment>();
		for (int iter = 0; iter < limitKeepSolutions; iter++) {
			int maxFitness = 0, maxIdx = -1;
			Environment bestEnv = null;
			ArrayList<boolean[]> bestPath = null;
			for (int i = 0; i < oP.size(); i++) {
				if ( !used[i] && (maxIdx == -1 || fitness[i] > maxFitness || oE.get(i).isLevelFinished()) ) {
					maxFitness = (oE.get(i).isLevelFinished()? LEVELFINISHED: fitness[i]);
					maxIdx = i;
					bestEnv = oE.get(i);
					bestPath = oP.get(i);
				}
			}
			if (maxIdx == -1) break;
			if (bestEnv.isLevelFinished()) {
				solved = true;
				return bestPath; //Found solution
			}
			dumpPath(bestPath);
			candidatePaths.add(bestPath);
			candidateEnvironments.add(bestEnv);
			used[maxIdx] = true;
		}
		
	}
	System.err.println("BAD ENDING!!!!!");
	return null;
}

/**
 * @param repetitionsOfSingleEpisode
 * @return boolean flag whether controller is disqualified or not
 */
public boolean runSingleEpisode(final int repetitionsOfSingleEpisode)
{
    long c = System.currentTimeMillis();
    for (int r = 0; r < repetitionsOfSingleEpisode; ++r)
    {
        this.reset();
		ArrayList<boolean[]> result = solve(copy(environment));

		dumpPath(result);

		//replay
        if(vis) {
			options.setVisualization(vis);
        	environment.reset(options);
		}
		for(int i=0;!environment.isLevelFinished();++i){
			environment.tick();
			environment.performAction(result.get(i));
			System.err.println("Fitness = " + calculateFitness(environment));
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
