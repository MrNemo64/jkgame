package me.nemo_64.sdp.player.util;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Objects;
import java.util.function.Consumer;

public class MovementListener extends JFrame implements KeyListener {

    private JButton button(String name, Consumer<String> listener, JFrame frame) {
        JButton button = new JButton(name);
        button.setPreferredSize(new Dimension(64, 64));
        button.addActionListener((e) -> {
            listener.accept(name);
            frame.requestFocus();
        });
        return button;
    }

    private final JButton northWestButton = button("NW", this::processMovement, this);
    private final JButton northButton = button("N", this::processMovement, this);
    private final JButton northEastButton = button("NE", this::processMovement, this);
    private final JButton westButton = button("W", this::processMovement, this);
    private final JButton eastButton = button("E", this::processMovement, this);
    private final JButton southWestButton = button("SW", this::processMovement, this);
    private final JButton southButton = button("S", this::processMovement, this);
    private final JButton southEastButton = button("SE", this::processMovement, this);

    private final Consumer<String> listener;

    public MovementListener(Consumer<String> listener) {
        this.listener = Objects.requireNonNull(listener);
        setTitle("Movement");
        setLayout(new GridLayout(3, 3));
        add(northWestButton);
        add(northButton);
        add(northEastButton);
        add(westButton);
        add(new JPanel());
        add(eastButton);
        add(southWestButton);
        add(southButton);
        add(southEastButton);

        setFocusable(true);
        addKeyListener(this);

        pack();
        setResizable(false);
    }

    private void processMovement(String movement) {
        listener.accept(movement);
    }

    @Override
    public void keyTyped(KeyEvent e) {
        switch (e.getKeyChar()) {
            case 'a' -> processMovement("W");
            case 'w' -> processMovement("N");
            case 's' -> processMovement("S");
            case 'd' -> processMovement("E");
            case 'q' -> processMovement("NW");
            case 'e' -> processMovement("NE");
            case 'z' -> processMovement("SW");
            case 'c' -> processMovement("SE");
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }
}
