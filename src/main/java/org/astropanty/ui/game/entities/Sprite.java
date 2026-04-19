package org.astropanty.ui.game.entities;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

public class Sprite {
	protected Image img;
	protected double xPos, yPos;
	protected double width;
	protected double height;
	protected double rotation;

	public Sprite(double xPos, double yPos, Image image) {
		this.xPos = xPos;
		this.yPos = yPos;
		this.loadImage(image);
		this.rotation = 0;
	}

	private void setSize() {
		this.width = this.img.getWidth();
		this.height = this.img.getHeight();
	}

	private void loadImage(Image img) {
		try {
			this.img = img;
			this.setSize();
		} catch (Exception e) {
		}
	}

	public void render(GraphicsContext gc) {
		gc.save();
		gc.translate(xPos + width / 2, yPos + height / 2);
		gc.rotate(rotation);
		gc.translate(-width / 2, -height / 2);
		gc.drawImage(this.img, 0, 0);
		gc.restore();
	}

	public double getRotation() {
		return this.rotation;
	}

	public Image getImage() {
		return this.img;
	}

	public double getWidth() {
		return this.width;
	}

	public double getHeight() {
		return this.height;
	}

	public double getXPos() {
		return this.xPos;
	}

	public double getYPos() {
		return this.yPos;
	}

	public void setXPos(double newX) {
		this.xPos = newX;
	}

	public void setYPos(double yPos) {
		this.yPos = yPos;
	}

	public void setRotation(double r) {
		this.rotation = r;
	}

	public void setImage(Image img) {
		this.img = img;
	}
}
