import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class NotifyQueueTest {
    static int checks;
    static void check(boolean b,String name){if(!b)throw new AssertionError(name);checks++;}
    public static void main(String[] args)throws Exception {
        NotifyQueue q=new NotifyQueue(10,3,100);NotifyQueue.Event event=new NotifyQueue.Event("e","user","hello");
        check(q.enqueue(event,0),"first enqueue");check(!q.enqueue(event,0),"dedup");
        try{q.enqueue(new NotifyQueue.Event("e","other","hello"),0);throw new AssertionError();}catch(IllegalArgumentException expected){check(true,"conflict");}
        NotifyQueue.Transport fail=e->{throw new Exception("outage");};q.tick(0,fail);check(q.outcome("e").dueAt()==100,"first delay");
        check(!q.tick(99,fail),"not due");q.tick(100,fail);check(q.outcome("e").dueAt()==300,"exponential delay");q.tick(300,fail);
        check(q.outcome("e").state()==NotifyQueue.State.DEAD&&q.pendingCount()==0,"dead letter");
        q.redrive("e",500);AtomicInteger sends=new AtomicInteger();q.tick(500,e->sends.incrementAndGet());check(sends.get()==1&&q.outcome("e").state()==NotifyQueue.State.DELIVERED,"redrive success");
        check(!q.enqueue(event,600),"dedup after delivery");
        NotifyQueue small=new NotifyQueue(1,1,100);small.enqueue(event,0);try{small.enqueue(new NotifyQueue.Event("b","user","hi"),0);throw new AssertionError();}catch(IllegalStateException expected){check(small.pendingCount()==1,"capacity");}
        NotifyQueue ordered=new NotifyQueue(10,1,100);ordered.enqueue(new NotifyQueue.Event("b","u","b"),0);ordered.enqueue(new NotifyQueue.Event("a","u","a"),0);List<String> sent=new ArrayList<>();ordered.tick(0,e->sent.add(e.id()));ordered.tick(0,e->sent.add(e.id()));check(sent.equals(List.of("a","b")),"stable ordering");
        NotifyQueue race=new NotifyQueue(5,3,100);AtomicInteger accepted=new AtomicInteger();ExecutorService pool=Executors.newFixedThreadPool(8);var futures=new ArrayList<Future<?>>();
        for(int i=0;i<100;i++)futures.add(pool.submit(()->{if(race.enqueue(event,0))accepted.incrementAndGet();}));for(var f:futures)f.get();pool.shutdown();check(accepted.get()==1&&race.pendingCount()==1,"concurrent dedup");
        NotifyQueue overflow=new NotifyQueue(5,3,100);overflow.enqueue(event,Long.MAX_VALUE);try{overflow.tick(Long.MAX_VALUE,fail);throw new AssertionError();}catch(ArithmeticException expected){check(overflow.pendingCount()==1&&overflow.outcome("e").attempts()==0,"overflow retains job");}
        System.out.println("NotifyQueue: "+checks+" checks passed");
    }
}
