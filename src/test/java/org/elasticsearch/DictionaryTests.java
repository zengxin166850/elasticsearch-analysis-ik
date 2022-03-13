package org.elasticsearch;

import org.junit.Test;
import org.wltea.analyzer.dic.DictSegment;
import org.wltea.analyzer.dic.Dictionary;
import org.wltea.analyzer.dic.Hit;

import java.io.File;
import java.nio.file.Path;

public class DictionaryTests {
    static final String configDir = "I:\\ideaWorkspace\\opensource\\elasticsearch-analysis-ik\\config";
    Dictionary dictionary = new Dictionary();


    @Test
    public void buildSegmentTest(){
        DictSegment mainDict = new DictSegment((char) 0);
        DictSegment stopDict = new DictSegment((char) 0);
        Path mainPath = new File(configDir + "\\main.dic").toPath();
        Path stopPath = new File(configDir + "\\stopword.dic").toPath();
        // 构建字典树
        dictionary.loadDictFile(mainDict,mainPath,false,"mainDict");
        dictionary.loadDictFile(stopDict,stopPath,false,"stopDict");

        //查询匹配
        Hit match = mainDict.match("一一对应".toCharArray());

    }
}
