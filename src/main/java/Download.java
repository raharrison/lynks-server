import io.webfolder.cdp.AdaptiveProcessManager;
import io.webfolder.cdp.Launcher;
import io.webfolder.cdp.session.Session;
import io.webfolder.cdp.session.SessionFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.awt.Desktop.getDesktop;
import static java.awt.Desktop.isDesktopSupported;
import static java.util.Arrays.asList;

public class Download {

    public static void main(String[] args) throws IOException {
        Launcher launcher = new Launcher();
        launcher.setProcessManager(new AdaptiveProcessManager());

        Path file = Paths.get("some.jpg");

        try (SessionFactory factory = launcher.launch(asList("--disable-gpu", "--headless", "--window-size=1280,720"))) {

            try (Session session = factory.create()) {
                System.out.println("navigate");

                session.navigate("https://ryanharrison.co.uk");
                session.waitDocumentReady(10000);
                session.activate();

                byte[] screen = session.getCommand().getPage().captureScreenshot();
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(screen));
                Image scaledImage = img.getScaledInstance(426, 240, Image.SCALE_SMOOTH);
                BufferedImage imageBuff = new BufferedImage(426, 240, BufferedImage.TYPE_INT_RGB);
                imageBuff.getGraphics().drawImage(scaledImage, 0, 0, Color.BLACK, null);
                imageBuff.getGraphics().dispose();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                ImageIO.write(imageBuff, "jpg", buffer);
                Files.write(file, buffer.toByteArray());
            }
        } finally {
            launcher.getProcessManager().kill();
        }

        if (isDesktopSupported()) {
            getDesktop().open(file.toFile());
        }

    }

    public static void saveScaledImage(BufferedImage sourceImage, int w, int h, String outputFile) {
        try {
            int width = sourceImage.getWidth();
            int height = sourceImage.getHeight();

            if (width > height) {
                float extraSize = height - h;
                float percentHight = (extraSize / height) * 100;
                float percentWidth = width - ((width / w) * percentHight);
                BufferedImage img = new BufferedImage((int) percentWidth, h, BufferedImage.TYPE_INT_RGB);
                Image scaledImage = sourceImage.getScaledInstance((int) percentWidth, h, Image.SCALE_SMOOTH);
                img.createGraphics().drawImage(scaledImage, 0, 0, null);
                BufferedImage img2 = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
                img2 = img.getSubimage((int) ((percentWidth - w) / 2), 0, w, h);

                ImageIO.write(img2, "jpg", new File(outputFile));
            } else {
                float extraSize = width - w;
                float percentWidth = (extraSize / width) * 100;
                float percentHight = height - ((height / h) * percentWidth);
                BufferedImage img = new BufferedImage(w, (int) percentHight, BufferedImage.TYPE_INT_RGB);
                Image scaledImage = sourceImage.getScaledInstance(w, (int) percentHight, Image.SCALE_SMOOTH);
                img.createGraphics().drawImage(scaledImage, 0, 0, null);
                BufferedImage img2 = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
                img2 = img.getSubimage(0, (int) ((percentHight - h) / 2), w, h);

                ImageIO.write(img2, "jpg", new File(outputFile));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
