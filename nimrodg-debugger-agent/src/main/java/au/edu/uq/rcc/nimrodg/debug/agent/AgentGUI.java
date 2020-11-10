/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2019 The University of Queensland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.edu.uq.rcc.nimrodg.debug.agent;

import au.edu.uq.rcc.nimrodg.master.sig.SigUtils;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.DefaultComboBoxModel;

public class AgentGUI extends javax.swing.JFrame {

	private final ActionListener listener;
	private final DefaultComboBoxModel<String> sigModel;

	public AgentGUI() {
		this((ActionEvent e) -> {
		});
	}

	public AgentGUI(ActionListener listener) {
		this.listener = listener;
		initComponents();
		this.addWindowListener(new _WindowAdapter());
		sigModel = new DefaultComboBoxModel<>(SigUtils.ALGORITHMS.keySet().stream()
                        .sorted().toArray(String[]::new));
		sigModel.setSelectedItem(SigUtils.DEFAULT_ALGORITHM);
		sigingAlgorithm.setModel(sigModel);
	}

	public void setConnectProgress(float perc) {
		connectProgressBar.setValue((int)(perc * 100));
	}

	public void setConnectStatus(String status, Color colour) {
		connectProgressBar.setString(status);
		//connectProgressBar.setForeground(colour);
	}

	public AgentControlPanel getAgentPanel() {
		return agentPanel;
	}

	public ILogger getLogger() {
		return logPanel;
	}

	public AgentStatusPanel getStatusPanel() {
		return statusPanel;
	}

	public enum ConnectionPanelState {
		CONNECT,
		DISCONNECT,
		LOCKED
	}

	public void setConnectionPanelState(ConnectionPanelState state) {
		if(state == ConnectionPanelState.CONNECT) {
			connectBtn.setText("Connect");
			connectBtn.setEnabled(true);
			amqpUri.setEditable(true);
			routingKey.setEditable(true);
		} else if(state == ConnectionPanelState.DISCONNECT) {
			connectBtn.setText("Disconnect");
			connectBtn.setEnabled(true);
			amqpUri.setEditable(false);
			routingKey.setEditable(false);
		} else {
			assert state == ConnectionPanelState.LOCKED;
			connectBtn.setEnabled(false);
			amqpUri.setEditable(false);
			routingKey.setEditable(false);
		}
	}

	public String getAMQPUri() {
		return amqpUri.getText();
	}

	public String getRoutingKey() {
		return routingKey.getText();
	}

	public String getTransferURI() {
		return transferUri.getText();
	}

	public String getSigningAlgorithm() {
		return (String)sigingAlgorithm.getSelectedItem();
	}

	public String getSecretKey() {
		return secretKey.getText();
	}

	public boolean getShowMessageWindow() {
		return showMessageWindow.isSelected();
	}

	@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        javax.swing.JLabel jLabel3 = new javax.swing.JLabel();
        amqpUri = new javax.swing.JTextField();
        connectBtn = new javax.swing.JButton();
        javax.swing.JLabel jLabel4 = new javax.swing.JLabel();
        transferUri = new javax.swing.JTextField();
        javax.swing.JLabel jLabel7 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel5 = new javax.swing.JLabel();
        routingKey = new javax.swing.JTextField();
        javax.swing.JLabel jLabel6 = new javax.swing.JLabel();
        secretKey = new javax.swing.JTextField();
        sigingAlgorithm = new javax.swing.JComboBox<>();
        javax.swing.JSplitPane jSplitPane1 = new javax.swing.JSplitPane();
        jPanel4 = new javax.swing.JPanel();
        agentPanel = new au.edu.uq.rcc.nimrodg.debug.agent.AgentControlPanel();
        statusPanel = new au.edu.uq.rcc.nimrodg.debug.agent.AgentStatusPanel();
        logPanel = new au.edu.uq.rcc.nimrodg.debug.agent.LogPanel();
        jPanel2 = new javax.swing.JPanel();
        connectProgressBar = new javax.swing.JProgressBar();
        jMenuBar1 = new javax.swing.JMenuBar();
        javax.swing.JMenu jMenu1 = new javax.swing.JMenu();
        javax.swing.JMenuItem jMenuItem2 = new javax.swing.JMenuItem();
        javax.swing.JPopupMenu.Separator jSeparator2 = new javax.swing.JPopupMenu.Separator();
        javax.swing.JMenuItem jMenuItem3 = new javax.swing.JMenuItem();
        jMenu2 = new javax.swing.JMenu();
        showMessageWindow = new javax.swing.JCheckBoxMenuItem();

        setTitle("Nimrod/G Agent Debugger");

        jLabel3.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        jLabel3.setText("AMQP URI");
        jLabel3.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);

        amqpUri.setText("amqp://guest:guest@127.0.0.1/nimrod");

        connectBtn.setText("Connect");
        connectBtn.setActionCommand("connect");
        connectBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                onActionPerformed(evt);
            }
        });

        jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        jLabel4.setText("Transfer URI");
        jLabel4.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);

        transferUri.setText("file:///home/zane/Desktop/agentwork");

        jLabel7.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        jLabel7.setText("Algorithm");
        jLabel7.setToolTipText("");
        jLabel7.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);

        jLabel5.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        jLabel5.setText("Routing Key");
        jLabel5.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);

        routingKey.setText("iamthemaster");

        jLabel6.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        jLabel6.setText("Secret Key");
        jLabel6.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);

        secretKey.setText("abc123");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(4, 4, 4)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(amqpUri, javax.swing.GroupLayout.PREFERRED_SIZE, 445, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel5)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(routingKey))
                    .addComponent(transferUri, javax.swing.GroupLayout.DEFAULT_SIZE, 693, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(secretKey, javax.swing.GroupLayout.PREFERRED_SIZE, 141, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(connectBtn))
                    .addComponent(sigingAlgorithm, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(amqpUri, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3)
                    .addComponent(connectBtn)
                    .addComponent(jLabel5)
                    .addComponent(routingKey, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6)
                    .addComponent(secretKey, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(sigingAlgorithm)
                    .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(transferUri, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabel4)
                        .addComponent(jLabel7)))
                .addContainerGap())
        );

        jSplitPane1.setDividerLocation(300);
        jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        jSplitPane1.setResizeWeight(1.0);

        jPanel4.setPreferredSize(new java.awt.Dimension(1108, 300));

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(agentPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(statusPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 762, Short.MAX_VALUE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(statusPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 299, Short.MAX_VALUE)
            .addComponent(agentPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
        );

        jSplitPane1.setLeftComponent(jPanel4);
        jSplitPane1.setRightComponent(logPanel);

        jPanel2.setPreferredSize(new java.awt.Dimension(400, 24));
        jPanel2.setLayout(new javax.swing.BoxLayout(jPanel2, javax.swing.BoxLayout.LINE_AXIS));

        connectProgressBar.setMaximumSize(new java.awt.Dimension(32767, 20));
        connectProgressBar.setMinimumSize(new java.awt.Dimension(128, 20));
        connectProgressBar.setPreferredSize(new java.awt.Dimension(128, 20));
        connectProgressBar.setString("");
        connectProgressBar.setStringPainted(true);
        jPanel2.add(connectProgressBar);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 1104, Short.MAX_VALUE)
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 434, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        jMenu1.setMnemonic('F');
        jMenu1.setText("File");
        jMenu1.setActionCommand("");

        jMenuItem2.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        jMenuItem2.setMnemonic('C');
        jMenuItem2.setText("Clear Log");
        jMenuItem2.setToolTipText("");
        jMenuItem2.setActionCommand("file->clear_log");
        jMenuItem2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                onActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem2);
        jMenu1.add(jSeparator2);

        jMenuItem3.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        jMenuItem3.setMnemonic('x');
        jMenuItem3.setText("Exit");
        jMenuItem3.setActionCommand("file->exit");
        jMenuItem3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                onActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem3);

        jMenuBar1.add(jMenu1);

        jMenu2.setText("View");

        showMessageWindow.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        showMessageWindow.setText("Show Message Window");
        showMessageWindow.setActionCommand("view->show_message_window");
        showMessageWindow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                onActionPerformed(evt);
            }
        });
        jMenu2.add(showMessageWindow);

        jMenuBar1.add(jMenu2);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void onActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_onActionPerformed
		listener.actionPerformed(evt);
    }//GEN-LAST:event_onActionPerformed

	private class _WindowAdapter extends WindowAdapter {

		@Override
		public void windowClosing(WindowEvent e) {
			AgentGUI.this.dispose();
		}
	}
//
//	/**
//	 * @param args the command line arguments
//	 */
//	public static void main(String args[]) {
//		SwingUtilities.invokeLater(() -> {
//			new AgentGUI().setVisible(true);
//		});
//	}

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private au.edu.uq.rcc.nimrodg.debug.agent.AgentControlPanel agentPanel;
    private javax.swing.JTextField amqpUri;
    private javax.swing.JButton connectBtn;
    private javax.swing.JProgressBar connectProgressBar;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private au.edu.uq.rcc.nimrodg.debug.agent.LogPanel logPanel;
    private javax.swing.JTextField routingKey;
    private javax.swing.JTextField secretKey;
    private javax.swing.JCheckBoxMenuItem showMessageWindow;
    private javax.swing.JComboBox<String> sigingAlgorithm;
    private au.edu.uq.rcc.nimrodg.debug.agent.AgentStatusPanel statusPanel;
    private javax.swing.JTextField transferUri;
    // End of variables declaration//GEN-END:variables
}
