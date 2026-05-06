package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TemplatePattern {

    private static final Logger log = LoggerFactory.getLogger(TemplatePattern.class);

    abstract class Game {
        abstract void initialize();

        abstract void startPlay();

        abstract void endPlay();

        //模板
        public final void play() {

            //初始化游戏
            initialize();

            //开始游戏
            startPlay();

            //结束游戏
            endPlay();
        }
    }

    class Cricket extends Game {

        @Override
        void endPlay() {
            log.info("Cricket Game Finished!");
        }

        @Override
        void initialize() {
            log.info("Cricket Game Initialized! Start playing.");
        }

        @Override
        void startPlay() {
            log.info("Cricket Game Started. Enjoy the game!");
        }
    }

    class Football extends Game {

        @Override
        void endPlay() {
            log.info("Football Game Finished!");
        }

        @Override
        void initialize() {
            log.info("Football Game Initialized! Start playing.");
        }

        @Override
        void startPlay() {
            log.info("Football Game Started. Enjoy the game!");
        }
    }

    @Test
    public void test() {

        Game game = new Cricket();
        game.play();
        log.info("");
        game = new Football();
        game.play();
    }
}
