# [杂谈]图像预览

## The backend loads TIFF images and renders them as JPEG-encoded image streams for browser display.

```java
    @GetMapping("/directjpg")
    public void directjpg(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        BufferedImage image = getLowMemoryThumbnail("d:\\knowledge\\" + IMAGE_PATH, 141);
        if (image == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        BufferedImage jpegImage;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            jpegImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = jpegImage.createGraphics();
            g.setComposite(AlphaComposite.Src);
            g.drawImage(image, 0, 0, null);
            g.dispose();
        } else {
            jpegImage = image;
        }
        try (ServletOutputStream os = response.getOutputStream()) {
            boolean written = ImageIO.write(jpegImage, "jpg", os);
            if (!written) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No JPEG writer found");
                return;
            }
            os.flush();
        }
    }
```

```java
    @GetMapping("/directjpg")
    public void directjpg(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        try (ServletOutputStream os = response.getOutputStream()) {
            Thumbnails.of(new File("d:\\knowledge\\" + IMAGE_PATH)).scale(0.01).outputFormat("jpg").outputQuality(0.7f).toOutputStream(os);
            os.flush();
        }
    }
```