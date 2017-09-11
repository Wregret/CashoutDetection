package wregret

import org.apache.spark.sql.SparkSession


/**
 * Hello world!
 *
 */
object App {

  def main(args: Array[String]): Unit = {
    var spark=SparkSession.builder().appName("cashoutdetection").master("local").enableHiveSupport().getOrCreate()
    var sc=spark.sparkContext

    val fields=List("BillNumber","Account","Money","BillDate","BillExInfo","BillSiteID")
    val bcfields=sc.broadcast(fields)

    def isExceptTime(str:String):Boolean={
      var h=str.charAt(8)+str.charAt(9).toInt
      if(h<8||h>22){
        true
      }else{
        false
      }
    }

    //原本用来判断差额，后来不用了
    def isExceptDifference(x:Iterable[Double]):Boolean={
      var min=Double.MaxValue
      var max=Double.MinPositiveValue
      for(num<-x){
        if(num<min)min=num
        if(num>max)max=num
      }
      if(max-min>=3000)true
      else false
    }

    def isExceptInt(x:Iterable[Double]):Boolean={
      for(num<-x){
        if(num<2000||num%100!=0) return false
      }
      return true
    }


    /*
    * 规则：
    * 1、全部交易金额都是大额整数>=2000
    * 2、交易金额差距过大》=3000
    * 3、交易时间不对，在晚上10点到上午8点之间*/
    val exceptionAccount=sc.textFile("hdfs://localhost:9000/b_bill.csv").map(m=>m.split(",")).map(m=>Array(m(bcfields.value.indexOf("Account")).substring(1,m(bcfields.value.indexOf("Account")).length-2),m(bcfields.value.indexOf("Money")).substring(1,m(bcfields.value.indexOf("Money")).length-2),m(bcfields.value.indexOf("BillNumber")).substring(1,m(bcfields.value.indexOf("BillNumber")).length-2)))
    val ea_bigint=exceptionAccount.map(m=>(m(0),m(1).toDouble)).groupByKey().filter(m=>isExceptInt(m._2)).map(m=>m._1)
    val ea_bigdiff=exceptionAccount.map(m=>(m(0),m(1).toDouble)).groupByKey().filter(m=>(m._2.max-m._2.min)>=3000).map(m=>m._1)
    val ea_extime=exceptionAccount.filter(m=>isExceptTime(m(2))).map(m=>m(0))
    ea_bigint.union(ea_bigdiff).union(ea_extime).distinct().collect().foreach(m=>println(m))

  }
}
