package cn.net.pap.common.bitmap.exception;

public class Roaring64NavigableMapException extends RuntimeException {

    /**
     * serialVersionUID :
     */
    private static final long serialVersionUID = -7479182840398184195L;

    public Roaring64NavigableMapException(String message){
        super(message);
    }

    public Roaring64NavigableMapException(String message, Throwable cause){
        super(message,cause);
    }

}
