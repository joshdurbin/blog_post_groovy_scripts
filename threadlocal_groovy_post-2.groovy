@Grapes(@Grab(group='com.google.guava', module='guava', version='26.0-jre'))

import java.util.concurrent.atomic.LongAdder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.Random
import java.lang.ThreadLocal
import com.google.common.base.Stopwatch

class ReusableThread extends Thread {
  static def sleepDuration = ThreadLocal.withInitial({new Random().nextInt(1_000)})
  ReusableThread(Runnable runnable) {
    super(runnable)
  }
  void run() {
    super.run()
  }
}

class ReusableThreadFactory implements ThreadFactory {
  Thread newThread(Runnable runnable) {
    new ReusableThread(runnable)
  }
}

def executor = new ThreadPoolExecutor(
  5,                                          // starting number of threads
  25,                                         // max number of threads
  5,                                          // wait time value
  TimeUnit.SECONDS,                           // wait time unit
  new LinkedBlockingQueue(50),                // queue implementation and size
  new ReusableThreadFactory(),                // uses our thread factory ^^^
  new ThreadPoolExecutor.CallerRunsPolicy())  // blocks the caller if the queue blocks

def sleepTimeCounts = new ConcurrentHashMap()
def sleepTimeToThreadId = new ConcurrentHashMap()
def latch = new CountDownLatch(1_000)         // prevent progression to output until done
def stopwatch = Stopwatch.createStarted()     // overall timing

1_000.times {
  executor.submit {
    latch.countDown()
    def sleepDuration = ReusableThread.sleepDuration.get()
    sleepTimeCounts.computeIfAbsent(sleepDuration, { k -> new LongAdder() }).increment()
    sleepTimeToThreadId.computeIfAbsent(sleepDuration, { k -> Thread.currentThread().getId() })
    sleep(sleepDuration as Long)
  }
}

latch.await()
executor.shutdown()

def sleepTimeOutput = 'thread id %-5d completed %-5d times, sleeping %-5dms %n'

sleepTimeCounts.each { sleepTime, counter ->
  System.out.format(sleepTimeOutput, sleepTimeToThreadId.get(sleepTime), counter.intValue(), sleepTime)
}

println "----> ${sleepTimeCounts.values().sum()} threads finished in ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms"
