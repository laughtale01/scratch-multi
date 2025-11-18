/**
 * MinecraftEdu Scratch VM - Entry Point
 * This module extends the official Scratch VM with Minecraft extension support
 */

// Import the official Scratch VM
const VirtualMachine = require('scratch-vm');

// Import Minecraft extension
const Minecraft = require('./extensions/scratch3_minecraft');

// Register the Minecraft extension
VirtualMachine.EXTENSION_MAP.minecraft = Minecraft;

// Re-export everything from official scratch-vm
module.exports = VirtualMachine;
