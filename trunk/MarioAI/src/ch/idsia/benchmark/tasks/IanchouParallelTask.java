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
import ch.idsia.agents.controllers.ForwardAgent;
import ch.idsia.agents.controllers.ScaredShooty;
import ch.idsia.agents.controllers.SearchingAgent;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Vector;

/**
 * Created by IntelliJ IDEA.
 * User: Sergey Karakovskiy,
 * sergey@idsia.ch
 * Date: Mar 14, 2010 Time: 4:47:33 PM
 */

public class IanchouParallelTask implements Task
{
protected Environment environment;
private Agent agent;
protected MarioAIOptions options;
private String name = getClass().getSimpleName();
private EvaluationInfo evaluationInfo;
boolean vis;

private Vector<StatisticalSummary> statistics = new Vector<StatisticalSummary>();

class Simulator extends Thread{
	boolean done = false, solved=false;
	Environment environment;
	int[] seq;
	int len, head;
	Agent[] agent;
	List<boolean[]> trace;

	public Simulator(Environment environment, int[] seq, int head, int len){
		this.environment = environment;
		this.head = head;
		this.seq = seq;
		this.len = len;
	}

	public void run(){
		trace = new ArrayList<boolean[]>();
		agent = new Agent[2];
		agent[0] = new ForwardAgent();
		agent[1] = new ScaredShooty();
		for(int i=0;i<2;++i){
		    agent[i].setObservationDetails(environment.getReceptiveFieldWidth(),
		            environment.getReceptiveFieldHeight(),
		            environment.getMarioEgoPos()[0],
		            environment.getMarioEgoPos()[1]);
		}
		int now=1, index=0;

	    for(int i=0;i<len && environment.getMarioStatus()!= Mario.STATUS_DEAD && environment.getMarioMode()==2; ++i){
	    	if(index<seq.length && i==seq[index]){
	    		now = 1-now;
	    		index++;
	    	}
	    	agent[now].integrateObservation(environment);
	    	agent[now].giveIntermediateReward(environment.getIntermediateReward());
	    	boolean[] action = agent[now].getAction().clone();
	    	trace.add(action.clone());
	    	environment.performAction(action);
	    	environment.tick();
	    }

	    if(environment.getMarioStatus()!= Mario.STATUS_DEAD && environment.getMarioMode()==2)
	    	solved = true;
	    done = true;
	    System.err.println("end: " + head + " " + environment.getMarioFloatPos()[0]);
	}
}

class SimulatorManager extends Thread{
	final static int MAX = 10, THD_MAX = 8;
	public boolean solved;
	Queue<Simulator> queue;
	Simulator[] simulators;
	Simulator result;
	
	public SimulatorManager(){
		queue = new LinkedBlockingQueue<Simulator>();
		solved = false;
		simulators = new Simulator[THD_MAX];
	}

	public void addSimulator(Simulator simulator){
		queue.add(simulator);
	}

	public void run(){
		while(!solved){
			for(int i=0;!solved && i<THD_MAX;++i){
				if(simulators[i]==null || simulators[i].done){
					if(simulators[i]!=null && simulators[i].solved){
						result = simulators[i];
						solved = true;
					}else{
						while(queue.isEmpty())
							if(solved)
								return;
						simulators[i] = queue.poll();
						simulators[i].start();
					}
				}
			}
		}
	}
}

public IanchouParallelTask(MarioAIOptions marioAIOptions)
{
	System.err.println("Thread number: " + SimulatorManager.THD_MAX);
	environment = new MarioEnvironment();
	vis = marioAIOptions.getParameterValue("-vis").equals("on");
	marioAIOptions.setVisualization(false); //can't be true...
	if(marioAIOptions.getParameterValue("-ag").equals("ch.idsia.agents.controllers.human.HumanKeyboardAgent"));
		marioAIOptions.setParameterValue("-ag", "ch.idsia.agents.controllers.ForwardAgent");
	this.setOptionsAndReset(marioAIOptions);
}

byte[] copy(Environment src){
	ByteArrayOutputStream bos = null;
    try{
    	bos = new ByteArrayOutputStream();
    	ObjectOutputStream oos = new ObjectOutputStream(bos);
    	oos.writeObject(src);
    	oos.flush();
    	oos.close();
    }catch(Exception e){
    	e.printStackTrace();
    }
    return bos != null ? bos.toByteArray() : null;
}

Environment get(byte[] src){
	Environment dest = null;
	try{
		ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(src));
		dest = (Environment)in.readObject();
	}catch(Exception e){
		e.printStackTrace();
	}
    return dest;
}

void update(int x, List<boolean[]> trace, List<byte[]> env){
	environment = get(env.get(x));
    for(int i=x;i<trace.size();++i){
    	if(environment.isLevelFinished()){
    		System.err.println("you got trouble!");
    	}
    	environment.performAction(trace.get(i));
    	environment.tick();
    	if(i+1 < trace.size()){
    		env.set(i+1, copy(environment));
    	}
    }
}

int[] next(int[] seq, int range){
	if(seq.length==0)
		return null;
	if(seq[seq.length-1]+1 < range){
		++seq[seq.length-1];
		return seq;
	}
	for(int i=seq.length-2;i>=0;--i){
		if(seq[i]+1<seq[i]){
			seq[i]++;
			for(int j=1;i+j<seq.length;++j)
				seq[i+j] = seq[i]+j;
			return seq;
		}
	}
	return null;
}

/**
 * @param repetitionsOfSingleEpisode
 * @return boolean flag whether controller is disqualified or not
 */
public boolean runSingleEpisode(final int repetitionsOfSingleEpisode)
{
	List<boolean[]> trace = new ArrayList<boolean[]>();
	List<byte[]> env = new ArrayList<byte[]>();
	SimulatorManager simulatorManager;

    for (int r = 0; r < repetitionsOfSingleEpisode; ++r)
    {
        this.reset();
        while (!environment.isLevelFinished())
        {
            environment.tick();

            boolean solved = environment.getMarioMode()==2 && environment.getMarioStatus() != Mario.STATUS_DEAD;
            if(!solved){
            	simulatorManager = new SimulatorManager();
            	simulatorManager.start();
            	for(int i=trace.size()-1;i>=0 && !simulatorManager.solved;--i){
            		for(int j=0;j<3 && i+j<trace.size() && !simulatorManager.solved;++j){
            			int seq[] = new int[j];
            			for(int k=0;k<j;++k)
            				seq[k] = k;
            			do{
            				while(!simulatorManager.solved && simulatorManager.queue.size()>=SimulatorManager.MAX);
            				simulatorManager.addSimulator(new Simulator(get(env.get(i)), seq.clone(), i, trace.size()-i)); 
            			}while(!simulatorManager.solved && (seq = next(seq, trace.size()-i))!=null);
            		}
            	}

    			if(simulatorManager.solved){
    				List<boolean[]> actions = simulatorManager.result.trace;
    				int x = simulatorManager.result.head;
    				trace = trace.subList(0, x);
    				trace.addAll(actions);
    				update(x, trace, env);
    			}else{
                	System.err.println("QQ");
                	try{
                        FileOutputStream fos = new FileOutputStream("output");
                        ObjectOutputStream oos = new ObjectOutputStream(fos);
                        oos.writeObject(new ArrayList<boolean[]>(trace));
                        oos.flush();
                        oos.close();
                        return false;
                    }catch(Exception e){
                       	e.printStackTrace();
                    }
    			}
            }

            env.add(copy(environment));
            if (!GlobalOptions.isGameplayStopped)
            {
                agent.integrateObservation(environment);
                agent.giveIntermediateReward(environment.getIntermediateReward());

                boolean[] action = agent.getAction().clone();

                trace.add(action);
                environment.performAction(action);
            }
        }
 
        //replay
        if(vis){
        	options.setVisualization(vis);
        	environment.reset(options);
        	for(int i=0;i<trace.size() && !environment.isLevelFinished();++i){
        		environment.tick();
        		environment.performAction(trace.get(i));
        	}
        }

        //output trace
        try{
        	FileOutputStream fos = new FileOutputStream("output");
        	ObjectOutputStream oos = new ObjectOutputStream(fos);
        	oos.writeObject(new ArrayList<boolean[]>(trace));
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
