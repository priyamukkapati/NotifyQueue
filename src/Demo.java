import java.util.*;
public final class Demo {
    public static void main(String[] args){
        NotifyQueue queue=new NotifyQueue(1000,3,100);int accepted=0,duplicates=0;
        for(int i=0;i<1000;i++){var e=new NotifyQueue.Event("e"+i,"user"+i,"update");if(queue.enqueue(e,0))accepted++;if(!queue.enqueue(e,0))duplicates++;}
        Map<String,Integer> calls=new HashMap<>();NotifyQueue.Transport transport=e->{int n=calls.merge(e.id(),1,Integer::sum);int id=Integer.parseInt(e.id().substring(1));if(id%10==0||n==1)throw new Exception("Injected failure");};
        long start=System.nanoTime();for(long time:new long[]{0,100,300})while(queue.tick(time,transport)){}
        long delivered=queue.outcomes().stream().filter(o->o.state()==NotifyQueue.State.DELIVERED).count(),dead=queue.outcomes().stream().filter(o->o.state()==NotifyQueue.State.DEAD).count();
        System.out.printf("accepted=%d duplicates=%d delivered=%d dead=%d transport_attempts=%d pending=%d elapsed_ms=%.2f%n",accepted,duplicates,delivered,dead,calls.values().stream().mapToInt(Integer::intValue).sum(),queue.pendingCount(),(System.nanoTime()-start)/1e6);
        if(delivered!=900||dead!=100||queue.pendingCount()!=0)throw new AssertionError("Retry invariant failed");
    }
}
