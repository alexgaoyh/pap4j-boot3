package cn.net.pap.common.jsonorm.dto;

import java.io.Serializable;
import java.util.List;

public class JsonDTO2 implements Serializable {

    private List<CharDTO> chars;

    public static class CharDTO implements Serializable {

        private int distance;

        private Double[] coords;

        private Double[] box;

        private String text;

        public int getDistance() {
            return distance;
        }

        public void setDistance(int distance) {
            this.distance = distance;
        }

        public Double[] getBox() {
            return box;
        }

        public void setBox(Double[] box) {
            this.box = box;
        }

        public Double[] getCoords() {
            return coords;
        }

        public void setCoords(Double[] coords) {
            this.coords = coords;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    public List<CharDTO> getChars() {
        return chars;
    }

    public void setChars(List<CharDTO> chars) {
        this.chars = chars;
    }
}
