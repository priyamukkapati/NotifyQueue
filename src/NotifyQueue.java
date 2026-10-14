import java.util.*;

/** Deterministic local retry scheduler. Transport is injected; there is no email provider. */
public final class NotifyQueue {
    public enum State { PENDING, DELIVERED, DEAD }
    public record Event(String id,String recipient,String body) {}
    public record Outcome(Event event,State state,int attempts,long dueAt) {}
    @FunctionalInterface public interface Transport { void send(Event event)throws Exception; }
    private static final class Job {
        final Event event; State state=State.PENDING; int attempts; long dueAt;
        Job(Event event,long dueAt){this.event=event;this.dueAt=dueAt;}
        Outcome snapshot(){return new Outcome(event,state,attempts,dueAt);}
    }
    private final int capacity,maxAttempts;
    private final long baseDelay;
    private final Map<String,Job> jobs=new LinkedHashMap<>();
    private final PriorityQueue<Job> pending=new PriorityQueue<>(Comparator.comparingLong((Job j)->j.dueAt).thenComparing(j->j.event.id()));
    public NotifyQueue(int capacity,int maxAttempts,long baseDelay){
        if(capacity<=0||maxAttempts<=0||maxAttempts>30||baseDelay<=0)throw new IllegalArgumentException("Invalid queue settings");
        Math.multiplyExact(baseDelay,1L<<(maxAttempts-1));
        this.capacity=capacity;this.maxAttempts=maxAttempts;this.baseDelay=baseDelay;
    }
    public synchronized boolean enqueue(Event event,long now){
        validate(event,now);Job previous=jobs.get(event.id());
        if(previous!=null){if(!previous.event.equals(event))throw new IllegalArgumentException("Conflicting event ID");return false;}
        if(jobs.size()>=capacity)throw new IllegalStateException("Retained event limit reached");
        Job job=new Job(event,now);jobs.put(event.id(),job);pending.add(job);return true;
    }
    /** Processes one due job. Transport must be fast: it runs under this object's lock. */
    public synchronized boolean tick(long now,Transport transport){
        if(now<0||transport==null)throw new IllegalArgumentException("Invalid tick");
        Job job=pending.peek();if(job==null||job.dueAt>now)return false;
        // Calculate before removing, so overflow cannot lose a pending job.
        long nextDue=job.attempts+1<maxAttempts?Math.addExact(now,Math.multiplyExact(baseDelay,1L<<job.attempts)):now;
        pending.remove();job.attempts++;
        try{transport.send(job.event);job.state=State.DELIVERED;}
        catch(Exception failure){
            if(job.attempts>=maxAttempts)job.state=State.DEAD;
            else{job.dueAt=nextDue;pending.add(job);}
        }
        return true;
    }
    public synchronized Outcome outcome(String id){Job job=jobs.get(id);if(job==null)throw new NoSuchElementException("Event not found");return job.snapshot();}
    public synchronized List<Outcome> outcomes(){return jobs.values().stream().map(Job::snapshot).toList();}
    public synchronized int pendingCount(){return pending.size();}
    public synchronized void redrive(String id,long now){
        if(now<0)throw new IllegalArgumentException("Invalid time");Job job=jobs.get(id);
        if(job==null)throw new NoSuchElementException("Event not found");if(job.state!=State.DEAD)throw new IllegalStateException("Only dead jobs can be redriven");
        job.state=State.PENDING;job.attempts=0;job.dueAt=now;pending.add(job);
    }
    private static void validate(Event e,long now){
        if(e==null||e.id()==null||e.id().isBlank()||e.id().length()>100||e.recipient()==null||e.recipient().isBlank()
           ||e.recipient().length()>200||e.body()==null||e.body().isBlank()||e.body().length()>2000||now<0)throw new IllegalArgumentException("Invalid event");
    }
    public static void main(String[] args){
        NotifyQueue q=new NotifyQueue(10,3,100);q.enqueue(new Event("welcome-1","demo-user","Welcome"),0);
        q.tick(0,e->{throw new Exception("Simulated outage");});q.tick(100,e->System.out.println("Simulated send: "+e.body()));
        System.out.println(q.outcome("welcome-1"));
    }
}
