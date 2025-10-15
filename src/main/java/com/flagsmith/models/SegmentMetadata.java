package com.flagsmith.models;

/**
 * SegmentMetadata
 * 
 * <p>Additional metadata associated with a segment.
 * 
 */
public class SegmentMetadata {
  /**
   * Source
   * 
   * <p>How the segment was created.
   * If the segment was created via the API, this will be `API`.
   * If the segment was created to support identity overrides in local evaluation,
   * this will be `IDENTITY_OVERRIDES`.
   */
  public enum Source {
    API,
    IDENTITY_OVERRIDES;
  }

  private Integer flagsmithId;
  private Source source;

  /*
   * FlagsmithId
   * <p>The internal Flagsmith ID for the segment.
   * 
   * @return The flagsmithId
   */
  public Integer getFlagsmithId() {
    return flagsmithId;
  }

  /*
   * FlagsmithId
   * <p>The internal Flagsmith ID for the segment.
   * 
   * @param flagsmithId The flagsmithId
   */
  public void setFlagsmithId(Integer flagsmithId) {
    this.flagsmithId = flagsmithId;
  }

  /*
   * Source
   * <p>How the segment was created.
   * 
   * @return The source
   */
  public Source getSource() {
    return source;
  }

  /*
   * Source
   * <p>How the segment was created.
   * 
   * @param source The source
   */
  public void setSource(Source source) {
    this.source = source;
  }
}
