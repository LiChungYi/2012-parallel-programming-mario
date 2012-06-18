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

/**
 * @param repetitionsOfSingleEpisode
 * @return boolean flag whether controller is disqualified or not
 */
    class OperationCode{
    	int jumpOp, speedOp, rightOp, leftNum = 0;
	OperationCode(){
		jumpOp = -1;	//0, 1: short jump, 2: long jump
		speedOp = -1;	//0, 1
		rightOp = -1;	//0, 1
	}
	void next(Random r){
		if(jumpOp == -1)
			jumpOp = r.nextInt(3);
		if(speedOp == -1)
			speedOp = r.nextInt(2);
		if(rightOp == -1){
			rightOp = r.nextInt(2);
			if(rightOp == 1){
				if(r.nextInt(5) == 0)
					rightOp = 2;//right until reach the ground
			}
			else{
				if(r.nextInt(15) == 0){
					rightOp = 3;//backward
					leftNum = 7;
				}	
			}
		}
	}
	boolean[] getAction(Environment environment){
		boolean[] action = new boolean[Environment.numberOfKeys];

		if(jumpOp == 1 || jumpOp == 2){
			action[Mario.KEY_JUMP] = true;
		}
		if(speedOp == 1){
			action[Mario.KEY_SPEED] = true;
		}
		if(rightOp == 1 || rightOp == 2){
			action[Mario.KEY_RIGHT] = true;
		}
		if(rightOp == 3){
			action[Mario.KEY_LEFT] = true;
			leftNum -= 1;
		}
		if(jumpOp != 2)
			jumpOp = -1;
		else if(jumpOp == 2 && environment.isMarioOnGround())//long jump, end after on the ground
			jumpOp = -1;
		speedOp = -1;
		if(rightOp != 2 && rightOp != 3)
			rightOp = -1;
		else if(rightOp == 2 && environment.isMarioOnGround())
			rightOp = -1;
		else if(rightOp == 3 && leftNum == 0)
			rightOp = -1;

		return action;
	}
    }

public EvaluationInfo runSingleEpisode(Environment environment, final Vector<boolean[]> aFuturePath, Random r, final int length)
{
    //long c = System.currentTimeMillis();

    OperationCode operationCode = new OperationCode();
    aFuturePath.clear();
    while (!environment.isLevelFinished())
    {
	    operationCode.next(r);
	    boolean[] action = operationCode.getAction(environment); 
//	    System.err.println("JUMP " + action[Mario.KEY_JUMP] + ", SPEED " + action[Mario.KEY_SPEED] + ", RIGHT " + action[Mario.KEY_RIGHT]);
	    aFuturePath.add(action);
            environment.performAction(action);
	    environment.tick();

	    if(environment.getEvaluationInfo().distancePassedPhys >= 16*length)
		    break;
    }


    environment.closeRecorder(); //recorder initialized in environment.reset
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



void dumpPath(Vector<boolean[]> surePathGivenEnvironment){
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
	}
	catch(Exception e){
		e.printStackTrace();
	}
}

void myAssert(boolean s){
	if(s == false){
		Exception e = new Exception();
		e.printStackTrace();
		System.exit(-1);
	}
}

static final int nSolution = 100, targetLenStep = 5, nSolutionForAcceptableDecrease = 5;
int acceptableFitnessDecrease = 20;
static final int backTrack_nOperation = 40;
static final int FITNESS_WIN = 10000, FITNESS_FIRE_MARIO = 400, FITNESS_BIG_MARIO = 200, FITNESS_SMALL_MARIO = 100; //fitness = FITNESS_WIN + time * (FITNESS_FIRE_MARIO or FITNESS_BIG_MARIO)

public void doEpisodes(int amount, boolean verbose, final int repetitionsOfSingleEpisode)
{
    int targetLen = targetLenStep;
    //environmentPath.get(i) + surePathGivenEnvironment.get(i) => environmentPath.get(i+1)
    Vector<Environment> environmentPath = new Vector<Environment>();
    Vector<boolean[]> surePathGivenEnvironment = new Vector<boolean[]>();
    Vector<Vector<boolean[]> > futurePathList = new Vector<Vector<boolean[]> >();
    int[] fitness = new int[nSolution];
    Random random = new Random(0);
    for(int i = 0; i < nSolution; ++i)
    	futurePathList.add(new Vector<boolean[]>());
    initEnvironment.tick();				//the convension is "a tick first then (perform+tick)*"
    environmentPath.add(initEnvironment);

    int lastFitness = Integer.MAX_VALUE;
    int stuck = 0;
    while(true){
	    int foundSol = 0, foundAcceptableSol = 0;
	    for(int i = 0; i < nSolution; ++i)
		    fitness[i] = 0;

	    myAssert(surePathGivenEnvironment.size() == environmentPath.size()-1);
	    int wantEnvIndex = surePathGivenEnvironment.size();
	    if(environmentPath.get(wantEnvIndex) == null ){
		    int notNullIndex = surePathGivenEnvironment.size()-1;
		    while(environmentPath.get(notNullIndex) == null) --notNullIndex;

		    EnvironmentGenerator gen = new EnvironmentGenerator(environmentPath.get(notNullIndex));
		    environmentPath.set(wantEnvIndex, gen.copyEnvironment());
		    for(int i = notNullIndex; i < wantEnvIndex; ++i){
			    environmentPath.get(wantEnvIndex).performAction(surePathGivenEnvironment.get(i));
			    environmentPath.get(wantEnvIndex).tick();
		    }
	    }
	    EnvironmentGenerator gen = new EnvironmentGenerator(environmentPath.lastElement());

	    int maxIter = stuck == 0? 300: Integer.MAX_VALUE;
	    int iter;
	    for(iter = 0; iter < maxIter; ++iter){
//		    System.out.println("iter" + iter);
//		    System.out.println("surePathGivenEnvironment length = " + surePathGivenEnvironment.size() + " targetLen" + targetLen);
		    myAssert(surePathGivenEnvironment.size() == environmentPath.size()-1);

		    EvaluationInfo evaluationInfo = runSingleEpisode(gen.copyEnvironment(), futurePathList.get(foundSol), random, targetLen); 

		    if(evaluationInfo.marioMode == 0)//
			    fitness[foundSol] += FITNESS_SMALL_MARIO;
		    if(evaluationInfo.marioMode == 1)//big, no fire
			    fitness[foundSol] += FITNESS_BIG_MARIO;
		    if(evaluationInfo.marioMode == 2)//fire
			    fitness[foundSol] += FITNESS_FIRE_MARIO;
		    double l = evaluationInfo.timeLeft;
		    double u = evaluationInfo.timeSpent;
		    fitness[foundSol] *= (l/(l+u));

		    if(evaluationInfo.marioStatus == Mario.STATUS_WIN)
			    fitness[foundSol] += FITNESS_WIN;

//		    System.out.println("futurePath [" + foundSol + "] length = " + futurePathList.get(foundSol).size() + ", fitness = " + fitness[foundSol]);

		    if(evaluationInfo.marioStatus == Mario.STATUS_RUNNING || evaluationInfo.marioStatus == Mario.STATUS_WIN){
			    if(lastFitness-fitness[foundSol] < acceptableFitnessDecrease)	//useless for last partition
				    ++foundAcceptableSol;
			    ++foundSol;
		    }
		    else{//delete this Vector
			futurePathList.get(foundSol).clear();
			fitness[foundSol] = 0;
		    }

		    if(foundSol == nSolution || foundAcceptableSol == nSolutionForAcceptableDecrease)
			    break;
	    }

	    if(stuck == 0 && iter == maxIter){//fail, try search longer
		    myAssert(!(foundSol == nSolution || foundAcceptableSol == nSolutionForAcceptableDecrease) );//fail

		    stuck = 1;
		    int newSize = surePathGivenEnvironment.size()-backTrack_nOperation;
		    newSize = newSize >= 0? newSize: 0;
		    surePathGivenEnvironment.setSize(newSize); 
		    environmentPath.setSize(newSize+1);		//initEnvironment will always be kept
		    continue;
	    }
	    if(stuck == 1){//must succeed after stuck
		    myAssert(iter < maxIter);//succeed
		    myAssert(foundSol == nSolution || foundAcceptableSol == nSolutionForAcceptableDecrease);//succeed
		    stuck = 0;
	    }
		for(int i = 1; i < fitness.length; ++i){
			if(fitness[i] > fitness[0]){
				int tmp = fitness[0];
				fitness[0] = fitness[i];
				fitness[i] = tmp;

				Vector<boolean[]> tmpV = futurePathList.get(0);
				futurePathList.set(0, futurePathList.get(i));
				futurePathList.set(i, tmpV);
			}
		}	

	    //sort by fitness
	    for(int i = 1; i < futurePathList.size(); ++i)
		    if(fitness[0] < fitness[i]){
			    int tmp = fitness[i]; fitness[i] = fitness[0]; fitness[0] = tmp;
			    Vector<boolean[]> tmpV = futurePathList.get(i); 
			    futurePathList.set(i, futurePathList.get(0));
			    futurePathList.set(0, tmpV);
		    }

	    //survive => add the best to surePathGivenEnvironment
	    for(int i = 0; i < futurePathList.get(0).size(); ++i){
		    surePathGivenEnvironment.add(futurePathList.get(0).get(i));
		    environmentPath.add(null);
	    }
	    dumpPath(surePathGivenEnvironment);

	    if(fitness[0] >= FITNESS_WIN){
		    System.out.println("SUCCEED!!!!!!!");
		    return;
	    }

	    targetLen += targetLenStep;
	    lastFitness = fitness[0];   
    }

    //EvaluationInfo.numberOfElements
//	    environment.getEvaluationInfo();
}

/*
public void doReplay(){
	List<boolean[]> trace = new ArrayList<boolean[]>();
	options.setVisualization(true);
	initEnvironment.reset(options);
    try{
    	ObjectInputStream in = new ObjectInputStream(new FileInputStream("output"));
    	trace = (List<boolean[]>)in.readObject();
        for(int i=0;i<trace.size() && !initEnvironment.isLevelFinished();++i){
        	initEnvironment.performAction(trace.get(i));
        	initEnvironment.tick();
        }
    }catch(Exception e){
    	e.printStackTrace();
    }
}*/



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
