package cn.net.pap.example.doris.service.impl;

import cn.net.pap.example.doris.service.DorisService;
import cn.net.pap.example.doris.service.IDorisTransServiceInMysqlDB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("dorisTransServiceInMysqlDB")
public class DorisTransServiceInMysqlDBImpl implements IDorisTransServiceInMysqlDB {

    @Autowired
    private DorisService dorisService;

    /**
     * 这里 @Transactional 注解 配合内部的 getConnection 会报错
     * @return
     */
    @Override
    @Transactional
    public int updateTestThrowExceptionInMysqlDB() {
        for(int i = 0; i < 1000; i++) {
            System.out.println(i);
            dorisService.updateTestThrowExceptionInMysqlDB();
        }
        return 1;
    }

    /**
     * 与上面的方法做对照，这里 @Transactional 注解内部的 getConnection 改为从 dataSource 获取
     * @return
     */
    @Override
    @Transactional
    public int updateTestNoExceptionInMysqlDB() {
        for(int i = 0; i < 1000; i++) {
            System.out.println(i);
            dorisService.updateTestNoExceptionInMysqlDB();
        }
        return 1;
    }

}
