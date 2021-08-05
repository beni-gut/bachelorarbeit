package eu.wdaqa.qanary.watson;

public class NamedEntity {
    private String uri;
    private int start;
    private int end;
    private double confidence;

    public NamedEntity(String uri, int start, int end, double confidence) {
        this.start = start;
        this.end = end;
        this.uri = uri;
        this.confidence = confidence;
    }

    public NamedEntity(String uri, int start, int end) {
        this.start = start;
        this.end = end;
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    public double getConfidence() {
        return confidence;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    @Override
    public String toString() {
        return (uri + " at location: (" + this.getStart() + ", " + this.getEnd() + ") with probability: " + this.getConfidence());
    }
}
