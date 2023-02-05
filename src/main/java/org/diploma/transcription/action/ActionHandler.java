package org.diploma.transcription.action;

public class ActionHandler
{
    private String name;

    private String phrase;

    private String url;

    public ActionHandler(String name, String phrase, String url)
    {
        this.name = name;
        this.phrase = phrase;
        this.url = url;
    }

    public String getPhrase()
    {
        return this.phrase;
    }

    public String getUrl()
    {
        return url;
    }

    public String getName()
    {
        return name;
    }

    @Override
    public String toString()
    {
        return "ActionHandler{" +
            "name='" + name + '\'' +
            ", phrase='" + phrase + '\'' +
            ", url='" + url + '\'' +
            '}';
    }
}
