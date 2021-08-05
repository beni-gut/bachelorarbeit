package eu.wdaqa.qanary.watson;

public class NamedEntity {
    private double confidence;
    private int[] location;
    private String uri;

    public NamedEntity(String uri, int[] location, double confidence) {
        this.location = location;
        this.uri = uri;
        this.confidence = confidence;
    }

    public NamedEntity(String uri, int[] location) {
        this.location = location;
        this.uri = uri;
    }

    public int[] getLocation() {
        return location;
    }

    public String getUri() {
        return uri;
    }

    public Double getConfidence() {
        return confidence;
    }

    public int getStart() {
        return location[0];
    }

    public int getEnd() {
        return location[1];
    }

    @Override
    public String toString() {
        return (uri + " at location: (" + this.getStart() + ", " + this.getEnd() + ") with probability: " + this.getConfidence());
    }
}
