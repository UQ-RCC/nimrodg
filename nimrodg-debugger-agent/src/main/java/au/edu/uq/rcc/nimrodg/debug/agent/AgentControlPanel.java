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

import au.edu.uq.rcc.nimrodg.agent.Agent;
import au.edu.uq.rcc.nimrodg.api.AgentInfo;

import java.util.ArrayList;

public class AgentControlPanel extends javax.swing.JPanel {

	public interface Listener {

		void onSubmit(String job);

		void onPing();

		void onCancel();

		void onTerminate();

		void onReset();
	}

	private final ArrayList<Listener> listeners;

	public AgentControlPanel() {
		listeners = new ArrayList<>();
		initComponents();
		resetBtn.setEnabled(true);
	}

	public void addListener(Listener l) {
		listeners.add(l);
	}

	public void removeListener(Listener l) {
		listeners.remove(l);
	}

	public void setState(AgentInfo.State state) {
		if(state == AgentInfo.State.SHUTDOWN) {
			waitingForAgent.setSelected(true);
			sendingInit.setSelected(true);
			shutdown.setSelected(true);
			submitBtn.setEnabled(false);
			pingBtn.setEnabled(false);
			cancelBtn.setEnabled(false);
			terminateBtn.setEnabled(false);
		} else if(state == AgentInfo.State.WAITING_FOR_HELLO) {
			waitingForAgent.setSelected(true);
			sendingInit.setSelected(false);
			shutdown.setSelected(false);
			submitBtn.setEnabled(false);
			pingBtn.setEnabled(false);
			cancelBtn.setEnabled(false);
			terminateBtn.setEnabled(false);
		} else if(state == AgentInfo.State.READY) {
			sendingInit.setSelected(true);
			waitingForAgent.setSelected(true);
			shutdown.setSelected(false);
			submitBtn.setEnabled(true);
			pingBtn.setEnabled(true);
			cancelBtn.setEnabled(false);
			terminateBtn.setEnabled(true);
		} else if(state == AgentInfo.State.BUSY) {
			sendingInit.setSelected(true);
			waitingForAgent.setSelected(true);
			shutdown.setSelected(false);
			submitBtn.setEnabled(false);
			pingBtn.setEnabled(true);
			cancelBtn.setEnabled(true);
			terminateBtn.setEnabled(true);
		}
	}

	@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        waitingForAgent = new javax.swing.JCheckBox();
        sendingInit = new javax.swing.JCheckBox();
        submitBtn = new javax.swing.JButton();
        cancelBtn = new javax.swing.JButton();
        terminateBtn = new javax.swing.JButton();
        shutdown = new javax.swing.JCheckBox();
        resetBtn = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        jobText = new javax.swing.JTextArea();
        pingBtn = new javax.swing.JButton();

        waitingForAgent.setText("Waiting for agent connection...");
        waitingForAgent.setEnabled(false);

        sendingInit.setText("Sending initialisation message...");
        sendingInit.setEnabled(false);

        submitBtn.setText("Submit");
        submitBtn.setEnabled(false);
        submitBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                submitBtnActionPerformed(evt);
            }
        });

        cancelBtn.setText("Cancel");
        cancelBtn.setEnabled(false);
        cancelBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelBtnActionPerformed(evt);
            }
        });

        terminateBtn.setText("Terminate");
        terminateBtn.setEnabled(false);
        terminateBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                terminateBtnActionPerformed(evt);
            }
        });

        shutdown.setText("Agent shutdown...");
        shutdown.setEnabled(false);

        resetBtn.setText("Reset");
        resetBtn.setEnabled(false);
        resetBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetBtnActionPerformed(evt);
            }
        });

        jobText.setColumns(20);
        jobText.setFont(new java.awt.Font("Courier New", 0, 10)); // NOI18N
        jobText.setRows(5);
        jobText.setText("parameter x text \"1\"\n\ntask main\n    redirect stdout to out\n    redirect stderr to out\n    exec uname -a\n    exec pwd\n    copy node:out root:out.txt\n    copy root:out.txt node:out2.txt\n    copy node:out2.txt node:out3.txt\nendtask"); // NOI18N
        jScrollPane2.setViewportView(jobText);

        pingBtn.setText("Ping");
        pingBtn.setEnabled(false);
        pingBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pingBtnActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2)
                    .addComponent(waitingForAgent, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(sendingInit, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(shutdown, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(resetBtn, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(submitBtn)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pingBtn)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelBtn)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(terminateBtn))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(waitingForAgent)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(sendingInit)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 209, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(submitBtn)
                    .addComponent(cancelBtn)
                    .addComponent(terminateBtn)
                    .addComponent(pingBtn))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(shutdown)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(resetBtn)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void submitBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_submitBtnActionPerformed
		listeners.forEach((Listener l) -> {
			l.onSubmit(jobText.getText());
		});
    }//GEN-LAST:event_submitBtnActionPerformed

    private void cancelBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelBtnActionPerformed
		listeners.forEach(Listener::onCancel);
    }//GEN-LAST:event_cancelBtnActionPerformed

    private void terminateBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_terminateBtnActionPerformed
		listeners.forEach(Listener::onTerminate);
    }//GEN-LAST:event_terminateBtnActionPerformed

    private void resetBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetBtnActionPerformed
		listeners.forEach(Listener::onReset);
    }//GEN-LAST:event_resetBtnActionPerformed

    private void pingBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pingBtnActionPerformed
        listeners.forEach(Listener::onPing);
    }//GEN-LAST:event_pingBtnActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelBtn;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTextArea jobText;
    private javax.swing.JButton pingBtn;
    private javax.swing.JButton resetBtn;
    private javax.swing.JCheckBox sendingInit;
    private javax.swing.JCheckBox shutdown;
    private javax.swing.JButton submitBtn;
    private javax.swing.JButton terminateBtn;
    private javax.swing.JCheckBox waitingForAgent;
    // End of variables declaration//GEN-END:variables
}
