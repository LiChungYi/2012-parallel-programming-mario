/*
 * Copyright (c) 2009-2010, Sergey Karakovskiy and Julian Togelius
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Mario AI nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
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

package ch.idsia.scenarios;

import ch.idsia.benchmark.tasks.BasicTask;
import ch.idsia.benchmark.tasks.ChungYiTask;
import ch.idsia.benchmark.tasks.IanchouParallelTask;
import ch.idsia.benchmark.tasks.IanchouTask;
import ch.idsia.benchmark.tasks.OurReplayTask;
import ch.idsia.benchmark.tasks.ChungYiParallelTask;
import ch.idsia.benchmark.tasks.ShikTask;
import ch.idsia.benchmark.tasks.TmtTask;
import ch.idsia.benchmark.tasks.ShikTask3;
import ch.idsia.benchmark.tasks.ShikTask4;
import ch.idsia.benchmark.tasks.LearningTask;
import ch.idsia.benchmark.tasks.Task;
import ch.idsia.tools.MarioAIOptions;

/**
 * Created by IntelliJ IDEA. User: Sergey Karakovskiy, sergey at idsia dot ch Date: Mar 17, 2010 Time: 8:28:00 AM
 * Package: ch.idsia.scenarios
 */
public final class Main
{
public static void main(String[] args)
{

    final MarioAIOptions marioAIOptions = new MarioAIOptions(args);

    Task basicTask;
    if(marioAIOptions.getTask().equals("ChungYi"))
    	basicTask = new ChungYiTask(marioAIOptions);
    else if(marioAIOptions.getTask().equals("ianchou"))
    	basicTask = new IanchouTask(marioAIOptions);
    else if(marioAIOptions.getTask().equals("ianchouP"))
    	basicTask = new IanchouParallelTask(marioAIOptions);
    else if(marioAIOptions.getTask().equals("Replay"))
    	basicTask = new OurReplayTask(marioAIOptions);
    else if(marioAIOptions.getTask().equals("shik"))
    	basicTask = new ShikTask(marioAIOptions);
    else if(marioAIOptions.getTask().equals("shik3"))
		basicTask = new ShikTask3(marioAIOptions);
    else if(marioAIOptions.getTask().equals("shik4"))
		basicTask = new ShikTask4(marioAIOptions);
    else if(marioAIOptions.getTask().equals("learn"))
		basicTask = new LearningTask(marioAIOptions);
    else if(marioAIOptions.getTask().equals("ChungYiParallel"))
    	basicTask = new ChungYiParallelTask(marioAIOptions);
    else if(marioAIOptions.getTask().equals("tmt"))
		basicTask = new TmtTask(marioAIOptions);
	else
    	basicTask = new BasicTask(marioAIOptions);
     
//    final BasicTaskClean basicTask = new BasicTaskClean(marioAIOptions);

    basicTask.setOptionsAndReset(marioAIOptions);
//    basicTask.doReplay();
    basicTask.doEpisodes(1,true,1);
    System.exit(0);

    /*
//        final String argsString = "-vis on";
    final MarioAIOptions marioAIOptions = new MarioAIOptions(args);
//        final Environment environment = new MarioEnvironment();
//        final Agent agent = new ForwardAgent();
//        final Agent agent = marioAIOptions.getAgent();
//        final Agent a = AgentsPool.loadAgent("ch.idsia.controllers.agents.controllers.ForwardJumpingAgent");
    final BasicTask basicTask = new BasicTask(marioAIOptions);
    
//        for (int i = 0; i < 10; ++i)
//        {
//            int seed = 0;
//            do
//            {
//                marioAIOptions.setLevelDifficulty(i);
//                marioAIOptions.setLevelRandSeed(seed++);
    basicTask.setOptionsAndReset(marioAIOptions);
//    basicTask.runSingleEpisode(1);
    basicTask.doEpisodes(1,true,1);
    //basicTask.doReplay();
//    System.out.println(basicTask.getEnvironment().getEvaluationInfoAsString());
//            } while (basicTask.getEnvironment().getEvaluationInfo().marioStatus != Environment.MARIO_STATUS_WIN);
//        }
//
    System.exit(0);
    */
}

}
