import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.Random
import java.lang.ThreadLocal

def loops = 10

class ExpensiveToConstructObject {
  ExpensiveToConstructObject() {
    def sleepTime = new Random().nextInt(1_000) as Long
    sleep(sleepTime)
    println "${Thread.currentThread().name} construction complete in ${sleepTime} ms"
  }
  void doThing() {
    println "${Thread.currentThread().name} running doThing()"
  }
}

class ReusableThread extends Thread {
  static def expensiveObject = ThreadLocal.withInitial({
    new ExpensiveToConstructObject()         // construct the expensive object
  })
  ReusableThread(Runnable runnable) {
    super(runnable)
  }
  void run() {
    super.run()
  }
}

class ReusableThreadFactory implements ThreadFactory {
  Thread newThread(Runnable runnable) {
    println "New thread requested..."
    new ReusableThread(runnable)
  }
}

def executor = new ThreadPoolExecutor(
  1,                                          // starting number of threads
  2,                                          // max number of threads
  5,                                          // wait time value
  TimeUnit.SECONDS,                           // wait time unit
  new LinkedBlockingQueue(5),                 // queue implementation and size
  new ReusableThreadFactory(),                // uses our thread factory ^^^
  new ThreadPoolExecutor.CallerRunsPolicy())  // blocks the caller if the queue blocks

def latch = new CountDownLatch(loops)

println "submitting jobs..."

loops.times {
  executor.submit {
		latch.countDown()
    def expensiveObject = ReusableThread.expensiveObject.get()
    expensiveObject.doThing() // do thing with an expensive object
  }
}

latch.await()
executor.shutdown()

