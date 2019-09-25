package au.edu.uq.rcc.nimrodg.debug.agent;

public class MessageWindow extends javax.swing.JFrame {

	public MessageWindow() {
		initComponents();
	}

	@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        messagePanel1 = new au.edu.uq.rcc.nimrodg.debug.agent.MessagePanel();

        setTitle("Messages");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(messagePanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 745, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(messagePanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 606, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

	public static void main(String args[]) {
		java.awt.EventQueue.invokeLater(() -> new MessageWindow().setVisible(true));
	}

	public MessagePanel getMessagePanel() {
		return messagePanel1;
	}

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private au.edu.uq.rcc.nimrodg.debug.agent.MessagePanel messagePanel1;
    // End of variables declaration//GEN-END:variables
}
