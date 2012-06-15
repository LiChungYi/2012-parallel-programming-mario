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
import ch.idsia.benchmark.mario.environments.Environment;
import ch.idsia.benchmark.mario.environments.MarioEnvironment;
import ch.idsia.tools.EvaluationInfo;
import ch.idsia.tools.MarioAIOptions;

import ch.idsia.tools.punj.PunctualJudge;

import ch.idsia.utils.statistics.StatisticalSummary;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Random;
import ch.idsia.benchmark.mario.engine.sprites.Mario;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Created by IntelliJ IDEA.
 * User: Sergey Karakovskiy,
 * sergey@idsia.ch
 * Date: Mar 14, 2010 Time: 4:47:33 PM
 */

public class ChungYiTask implements Task{
	ByteArrayOutputStream bos;	
	public class EnvironmentGenerator{
		EnvironmentGenerator(Environment src){
			try{
				bos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(bos);
				oos.writeObject(src);
				oos.flush();
				oos.close();
			}
			catch(Exception e){
				e.printStackTrace();
			}	
		}	
		Environment copyEnvironment(){
			Environment dest = null;
			try{
				ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
				dest = (Environment)in.readObject();
			}
			catch(Exception e){
				e.printStackTrace();
			}
			return dest;
		}
	}

protected Environment initEnvironment;
private Agent agent;
protected MarioAIOptions options;
private long COMPUTATION_TIME_BOUND = 42; // stands for prescribed  FPS 24.
private String name = getClass().getSimpleName();
//private EvaluationInfo evaluationInfo;

private Vector<StatisticalSummary> statistics = new Vector<StatisticalSummary>();

public ChungYiTask(MarioAIOptions marioAIOptions)
{
	initEnvironment = new MarioEnvironment();
	marioAIOptions.setVisualization(false); //can't be true... since we are copying environment
    this.setOptionsAndReset(marioAIOptions);
}



boolean[] actionCodeToByteArray(Integer actionCode){
    	    boolean[] action = new boolean[Environment.numberOfKeys];
	    if(actionCode == 0)
		    action[Mario.KEY_RIGHT] = true;
	    else if(actionCode == 1){
		    action[Mario.KEY_SPEED] = true;
		    action[Mario.KEY_RIGHT] = true;
	    }
	    else{
		    action[Mario.KEY_RIGHT] = true;
		    action[Mario.KEY_JUMP] = true;
	    }
	    return action;
}

/**
 * @param repetitionsOfSingleEpisode
 * @return boolean flag whether controller is disqualified or not
 */
public EvaluationInfo runSingleEpisode(Environment environment, final Vector<Integer> actionCodeList, Random r, final int length)
{
    //long c = System.currentTimeMillis();

    int count = 0;
    while (!environment.isLevelFinished())
    {
	    int actionCode;
	    if(count >= actionCodeList.size())
		    actionCodeList.add(r.nextInt(3));
	    actionCode = actionCodeList.get(count);
	    boolean[] action = actionCodeToByteArray(actionCode);
	    ++count;

            environment.performAction(action);
	    environment.tick();

	    if(environment.getEvaluationInfo().distancePassedPhys >= 16*length)
		    break;
    }


//    environment.closeRecorder(); //recorder initialized in environment.reset
//    environment.getEvaluationInfo().setTaskName(name);
//    this.evaluationInfo = environment.getEvaluationInfo().clone();

    return environment.getEvaluationInfo();
}

public Environment getEnvironment()
{
    return null; //environment;
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
}


void dumpPath(Vector<Integer> surePathGivenEnvironment){
	//output trace
	List<boolean[]> trace = new ArrayList();
	for(int i = 0; i < surePathGivenEnvironment.size(); ++i)
		trace.add(actionCodeToByteArray(surePathGivenEnvironment.get(i)));

	try{
		FileOutputStream fos = new FileOutputStream("ChungYi_marioTrace");
		ObjectOutputStream oos = new ObjectOutputStream(fos);
		oos.writeObject(trace);
		oos.flush();
		oos.close();
	}
	catch(Exception e){
		e.printStackTrace();
	}
}

public void doEpisodes(int amount, boolean verbose, final int repetitionsOfSingleEpisode)
{
    int nSolution = 50, targetLen = 5, targetLenStep = 5, acceptableFitnessDecrease = 0, nSolutionForAcceptableDecrease = 5;
    int backTrack_nOperation = 10;

    //environmentPath.get(i) + surePathGivenEnvironment.get(i) => environmentPath.get(i+1)
    Vector<Environment> environmentPath = new Vector<Environment>();
    Vector<Integer> surePathGivenEnvironment = new Vector<Integer>();
    Vector<Vector<Integer> > futurePathList = new Vector<Vector<Integer> >();
    int[] fitness = new int[nSolution];
    Random random = new Random(0);
    for(int i = 0; i < nSolution; ++i)
    	futurePathList.add(new Vector<Integer>());
    environmentPath.add(initEnvironment);

    int lastFitness = Integer.MAX_VALUE;
    while(true){
	    int iter = 0;
	    int foundSol = 0, foundAcceptableSol = 0;
	    while(foundSol != nSolution && foundAcceptableSol != nSolutionForAcceptableDecrease){
		    if(iter > 100){
			int newSize = surePathGivenEnvironment.size()-backTrack_nOperation;
			newSize = newSize >= 0? newSize: 0;
		 	surePathGivenEnvironment.setSize(newSize); 
			environmentPath.setSize(newSize+1);		//initEnvironment will always be kept
			iter -= 100;
		    }

		    System.out.println("iter" + iter);
		    System.out.println("surePathGivenEnvironment length = " + surePathGivenEnvironment.size());

	    	    for(int i = 0; i < nSolution; ++i)
			fitness[i] = 0;
		    
//		    if(foundSol >= futurePathList.size())
//			    futurePathList.add(new Vector<Integer>());
		    
		    if(surePathGivenEnvironment.size() != environmentPath.size()-1){
			    System.err.println("bug!");
			    System.exit(0);
		    }
		    EnvironmentGenerator gen = new EnvironmentGenerator(environmentPath.lastElement());
		    EvaluationInfo evaluationInfo = runSingleEpisode(gen.copyEnvironment(), futurePathList.get(foundSol), random, targetLen); 
		    if(evaluationInfo.marioStatus == Mario.STATUS_WIN){
			    System.err.println("succeed! + marioMode: " + evaluationInfo.marioMode);
			    dumpPath(surePathGivenEnvironment);
			return;
		    }

		    if(evaluationInfo.marioMode == 1)//big, no fire
			    fitness[foundSol] += 100;
		    if(evaluationInfo.marioMode == 2)//fire
			    fitness[foundSol] += 200;
		    double l = evaluationInfo.timeLeft;
		    double u = evaluationInfo.timeSpent;
		    fitness[foundSol] *= (l/(l+u));

		    System.out.println("futurePath [" + foundSol + "] length = " + futurePathList.get(foundSol).size() + ", fitness = " + fitness[foundSol]);

		    if(evaluationInfo.marioStatus == Mario.STATUS_RUNNING){
			    if(lastFitness-fitness[foundSol] < acceptableFitnessDecrease)
				    ++foundAcceptableSol;
			    ++foundSol;
		    }
		    else{//delete this Vector
			futurePathList.get(foundSol).clear();
		    }
		    ++iter;
	    }

	    //sort by fitness
	    for(int i = 0; i < futurePathList.size(); ++i)
		    for(int j = i + 1; j < futurePathList.size(); ++j){
		    	if(fitness[i] < fitness[j]){
				int tmp = fitness[i]; fitness[i] = fitness[j]; fitness[j] = tmp;
				Vector<Integer> tmpV = futurePathList.get(i); 
				futurePathList.set(i, futurePathList.get(j));
				futurePathList.set(j, tmpV);
			}
		    }
	    //survive => add the best to surePathGivenEnvironment
	    targetLen += targetLenStep;
	    for(int i = 0; i < futurePathList.get(0).size(); ++i){
		    surePathGivenEnvironment.add(futurePathList.get(0).get(i));

		    EnvironmentGenerator gen = new EnvironmentGenerator(environmentPath.lastElement());
		    Environment nextEnvironment = gen.copyEnvironment();
		    
		    boolean[] action = actionCodeToByteArray(surePathGivenEnvironment.lastElement());
		    nextEnvironment.performAction(action);
		    nextEnvironment.tick();
		    environmentPath.add(nextEnvironment);
	    }
	    lastFitness = fitness[0];   
    }


    //EvaluationInfo.numberOfElements
//	    environment.getEvaluationInfo();
}

public boolean isFinished()
{
    return false;
}

public void reset()
{
    agent = options.getAgent();
    initEnvironment.reset(options);
    agent.reset();
    agent.setObservationDetails(initEnvironment.getReceptiveFieldWidth(),
            initEnvironment.getReceptiveFieldHeight(),
            initEnvironment.getMarioEgoPos()[0],
            initEnvironment.getMarioEgoPos()[1]);
}

public String getName()
{
    return name;
}

public void printStatistics()
{
    //System.out.println(evaluationInfo.toString());
}

public EvaluationInfo getEvaluationInfo()
{
//    System.out.println("evaluationInfo = " + evaluationInfo);
    //return evaluationInfo;
    return null;
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
