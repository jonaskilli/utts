package com.github.jing332.tts_server_android

import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 *
 * ⚠️ 11-19：原来 import 了 `model.AnalyzeUrl`，但那个源文件整体被注释掉了（功能已迁走），
 * 导致**整个单元测试源集编译不过**（连别的测试文件一起挡）。故把 import 去掉——
 * 下面 testJs 的内容本就全被注释，没有实际引用。
 */
class HttpTtsUrlUnitTest {
//    @Test
//    fun testJs() {
////        val url =
////            "http://tsn.baidu.com/text2audio,{\"method\": \"POST\", \"body\": \"tex={{java.encodeURI(java.encodeURI(speakText))}}&spd={{(speakSpeed + 5) / 10 + 4}}&per=4114&cuid=baidu_speech_demo&idx=1&cod=2&lan=zh&ctp=1&pdt=220&vol=5&aue=6&pit=5&res_tag=audio\"}"
//        val url =
//            """  http://192.168.0.109:1233/api/ra,{"method":"POST","body":"<speak xmlns=\"http://www.w3.org/2001/10/synthesis\" xmlns:mstts=\"http://www.w3.org/2001/mstts\" xmlns:emo=\"http://www.w3.org/2009/10/emotionml\" version=\"1.0\" xml:lang=\"en-US\"><voice name=\"zh-CN-XiaoxiaoNeural\"><prosody rate=\"{{(speakSpeed -10) * 2}}%\" pitch=\"+0Hz\">{{speakText}}</prosody></voice></speak>"} """
//        val aurl = AnalyzeUrl(url)
//        println("baseUrl: " + aurl.eval().toString())
//    }
}